package org.example;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.PersonName;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.SpecificCharacterSet;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.BulkDataCreator;
import org.dcm4che3.io.DicomInputHandler;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.util.StreamUtils;
import org.dcm4che3.util.TagUtils;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class XMLWriter implements DicomInputHandler, BulkDataCreator {

    private static final String NAMESPACE = "http://dicom.nema.org/PS3.19/models/NativeDICOM";

    /**
     * Initially, the resulting xml is written to "outputDir/output.xml",
     * and after by the end of processing completion is moved to the proper place.
     * See {@link #endDataset(DicomInputStream)}
     */

    private static final String TMP_XML_FILE_NAME = "output.xml";
    private final Path outputDir;
    private File tmpXmlFile;
    private OutputStream outputStream;

    // next fields are used to build the final xml filename
    private String patientName;
    private String patientId;
    private String suffix;

    private XMLStreamWriter writer;

    public XMLWriter(Path outputDir) {
        this.outputDir = outputDir;
    }

    @Override
    public void readValue(DicomInputStream dis, Attributes attrs) throws IOException {
        int tag = dis.tag();
        if (TagUtils.isGroupLength(tag) || TagUtils.isPrivateCreator(tag)) {
            dis.readValue(dis, attrs);
            return;
        }
        VR vr = dis.vr();
        long len = dis.unsignedLength();
        startElement("DicomAttribute", collectElementAttributes(tag, vr, attrs.getPrivateCreator(tag)));

        if (vr != VR.SQ && len > 0L) {
            if (dis.isIncludeBulkDataURI()) {
                writeBulkData(createBulkData(dis));
            } else {
                byte[] b = dis.readValue();

                if (tag == Tag.TransferSyntaxUID || tag == Tag.SpecificCharacterSet) {
                    attrs.setBytes(tag, vr, b);
                }

                if (tag == Tag.SOPClassUID) {
                    String sopClass = vr.toStrings(b, dis.bigEndian(), attrs.getSpecificCharacterSet(vr)).toString();
                    this.suffix = FileUtils.getSuffixForSopClass(sopClass);
                }

                if (tag == Tag.PatientID) {
                    this.patientId = vr.toStrings(b, dis.bigEndian(), attrs.getSpecificCharacterSet(vr)).toString();
                }

                if (tag == Tag.PatientName) {
                    this.patientName = vr.toStrings(b, dis.bigEndian(), attrs.getSpecificCharacterSet(vr)).toString();
                }

                if (vr.isInlineBinary()) {
                    writeInlineBinary(dis.bigEndian()
                            ? vr.toggleEndian(b, false)
                            : b);
                } else {
                    writeValues(vr, b, dis.bigEndian(),
                            attrs.getSpecificCharacterSet(vr));
                }
            }
        } else {
            dis.readValue(dis, attrs);
        }
        endElement();
    }

    private void writeBulkData(BulkData bulkData) throws IOException {
        writeElement("BulkData", Map.of("uri", bulkData.getURI()), null);
    }


    private void writeValues(VR vr, Object val, boolean isBigEndian,
                             SpecificCharacterSet cs) throws IOException {
        if (vr.isStringType()) {
            val = vr.toStrings(val, isBigEndian, cs);
        }
        int vm = vr.vmOf(val);
        for (int i = 0; i < vm; i++) {
            String s = vr.toString(val, isBigEndian, i, null);
            var attrs = Map.of("number", Integer.toString(i + 1));
            if (vr == VR.PN) {
                PersonName pn = new PersonName(s, true);
                startElement("PersonName", attrs);
                writePNGroup("Alphabetic", pn, PersonName.Group.Alphabetic);
                writePNGroup("Ideographic", pn, PersonName.Group.Ideographic);
                writePNGroup("Phonetic", pn, PersonName.Group.Phonetic);
                endElement();
            } else {
                writeElement("Value", attrs, s);
            }
        }
    }

    private void writePNGroup(String qname, PersonName pn,
                              PersonName.Group group) throws IOException {
        if (pn.contains(group)) {
            startElement(qname);
            writeElement("FamilyName",
                    pn.get(group, PersonName.Component.FamilyName));
            writeElement("GivenName",
                    pn.get(group, PersonName.Component.GivenName));
            writeElement("MiddleName",
                    pn.get(group, PersonName.Component.MiddleName));
            writeElement("NamePrefix",
                    pn.get(group, PersonName.Component.NamePrefix));
            writeElement("NameSuffix",
                    pn.get(group, PersonName.Component.NameSuffix));
            endElement();
        }
    }

    private void writeInlineBinary(byte[] b) throws IOException {
        writeElement("InlineBinary", Base64.getEncoder().encodeToString(b));
    }

    private Map<String, String> collectElementAttributes(int tag, VR vr, String privateCreator) {
        var attrs = new HashMap<String, String>();
        attrs.put("tag", TagUtils.toHexString(tag));
        attrs.put("vr", vr.name());
        if (privateCreator != null) {
            attrs.put("privateCreator", privateCreator);
        }
        String keyword = ElementDictionary.keywordOf(tag, privateCreator);
        if (keyword != null) {
            attrs.put("keyword", keyword);
        }
        return Collections.unmodifiableMap(attrs);
    }

    @Override
    public void readValue(DicomInputStream dis, Sequence sequence) throws IOException {
        startElement("Item", Map.of(
                "number", Integer.toString(sequence.size() + 1))
        );
        dis.readValue(dis, sequence);
        endElement();
    }

    @Override
    public void readValue(DicomInputStream dis, Fragments fragments) throws IOException {
        if (dis.unsignedLength() < 1L) {
            return;
        }
        startElement("DataFragment", Map.of("number", Integer.toString(fragments.size())));
        if (dis.isIncludeBulkDataURI()) {
            writeBulkData(createBulkData(dis));
        } else {
            byte[] b = dis.readValue();
            if (dis.bigEndian()) {
                fragments.vr().toggleEndian(b, false);
            }
            writeInlineBinary(b);
        }
        endElement();
    }


    @Override
    public void startDataset(DicomInputStream dis) throws IOException {
        tmpXmlFile = Path.of(outputDir.toString(), TMP_XML_FILE_NAME).toFile();
        outputStream = new FileOutputStream(tmpXmlFile);
        try {
            this.writer = XMLOutputFactory.newInstance().createXMLStreamWriter(outputStream);
            this.writer.writeStartDocument();
            this.writer.writeStartElement("", "NativeDicomModel", NAMESPACE);
        } catch (XMLStreamException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void endDataset(DicomInputStream dicomInputStream) throws IOException {
        try {
            this.writer.writeEndElement();
            this.writer.writeEndDocument();
            this.writer.flush();
            this.writer.close();
        } catch (XMLStreamException e) {
            throw new IOException(e);
        } finally {
            this.outputStream.close();
        }
        // move result xml file
        Path xmlFilePath = outputDir.resolve(getFilePrefix() + ".xml");
        Files.move(tmpXmlFile.toPath(), xmlFilePath);
    }

    // helper functions
    private void startElement(String tagName) throws IOException {
        this.startElement(tagName, Collections.emptyMap());
    }

    private void startElement(String tagName, Map<String, String> attributes) throws IOException {
        try {
            this.writer.writeStartElement(NAMESPACE, tagName);
            for (Map.Entry<String, String> attr : attributes.entrySet()) {
                this.writer.writeAttribute(attr.getKey(), attr.getValue());
            }
        } catch (XMLStreamException e) {
            throw new IOException(e);
        }
    }

    private void endElement() throws IOException {
        try {
            this.writer.writeEndElement();
        } catch (XMLStreamException e) {
            throw new IOException(e);
        }
    }

    private void writeElement(String elementName, String value) throws IOException {
        writeElement(elementName, Collections.emptyMap(), value);
    }

    private void writeElement(String elementName, Map<String, String> attrs, String value) throws IOException {
        startElement(elementName, attrs);
        if (value != null) {
            try {
                this.writer.writeCharacters(value);
            } catch (XMLStreamException e) {
                throw new IOException(e);
            }
        }
        endElement();
    }

    private String getFilePrefix() {
        return  String.format("%s_%s", this.patientName, this.patientId);
    }

    @Override
    public BulkData createBulkData(DicomInputStream dis) throws IOException {
        File blkfile = File.createTempFile(getFilePrefix() + "_", this.suffix, this.outputDir.toFile());
        String blkURI = blkfile.toURI().toString();
        FileOutputStream blkOut = new FileOutputStream(blkfile);
        StreamUtils.copy(dis, blkOut, dis.unsignedLength());
        blkOut.close();
        return new BulkData(blkURI, 0L, dis.unsignedLength(), dis.bigEndian());
    }
}
