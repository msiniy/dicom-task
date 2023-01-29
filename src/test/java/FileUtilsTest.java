import org.example.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileUtilsTest {
    @Test
    void testGetFilenameWithoutExtension() {
        String name = FileUtils.getFileNameWithoutExtension(new File("test.txt"));
        assertEquals( "test", name);

        name = FileUtils.getFileNameWithoutExtension(new File("test"));
        assertEquals( "test", name);

        name = FileUtils.getFileNameWithoutExtension(new File("parent/dir/test.txt"));
        assertEquals("test", name);

        name = FileUtils.getFileNameWithoutExtension(new File("parent/dir/test"));
        assertEquals("test", name);
    }

    @Test
    void testGetSuffixForSubclass() {
        assertEquals(".pdf", FileUtils.getSuffixForSopClass("1.2.840.10008.5.1.4.1.1.104.1"));
        assertEquals(".jpg", FileUtils.getSuffixForSopClass("1.2.840.10008.5.1.4.1.1.77.1.4"));
    }


}
