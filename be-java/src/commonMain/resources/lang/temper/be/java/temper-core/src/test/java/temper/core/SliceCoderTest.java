package temper.core;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CoderResult;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SliceCoderTest {
    @Test
    void decodeFromSliceSuccess() throws CharacterCodingException {
        String input = "Hello World";
        ByteBuffer buffer = ByteBuffer.wrap("###Hello World###".getBytes(StandardCharsets.UTF_8));
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        // Decode only "Hello World" (offset 3, length 11)
        String result = Core.decodeFromSlice(buffer, 3, 11, decoder);
        assertEquals("Hello World", result);
        assertEquals(0, buffer.position(), "Position should be restored");
        assertEquals(buffer.capacity(), buffer.limit(), "Limit should be restored");
    }

    @Test
    void decodeFromSliceInvalidSequence() {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[]{(byte) 0xFF}); // Invalid UTF-8
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        assertThrows(CharacterCodingException.class, () -> Core.decodeFromSlice(buffer, 0, 1, decoder));
    }

    @Test
    void encodeIntoSliceWithPadding() {
        ByteBuffer buffer = ByteBuffer.allocate(10);
        String text = "ABC"; // 3 bytes in UTF-8
        CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();
        // Encode into a 5-byte slice at offset 2
        int written = Core.encodeIntoSlice(text, buffer, 2, 5, encoder, (byte) '_');
        assertEquals(3, written);
        // Check actual buffer contents
        byte[] data = buffer.array();
        assertEquals('A', (char)data[2]);
        assertEquals('B', (char)data[3]);
        assertEquals('C', (char)data[4]);
        assertEquals('_', (char)data[5]); // Padding
        assertEquals('_', (char)data[6]); // Padding
        assertEquals(0, buffer.position(), "State must be restored");
    }

    @Test
    void encodeIntoSliceTruncation() {
        ByteBuffer buffer = ByteBuffer.allocate(5);
        String text = "Too Long String";
        CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();
        // Buffer limit of 2 bytes should stop the encoder
        int written = Core.encodeIntoSlice(text, buffer, 0, 2, encoder, (byte) 0);
        assertEquals(2, written);
        assertEquals(0, buffer.position());
    }

    @Test
    void encodeIntoSlicePartialMultibyteFit() {
        // ✨ = 3 bytes (E2 9C A8)
        // 😀 = 4 bytes (F0 9F 98 80)
        String text = "✨😀";
        ByteBuffer buffer = ByteBuffer.allocate(10);
        CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();
        // Slice of 5 bytes at offset 0
        // Result: "✨" (3 bytes) fits, "😀" (4 bytes) fails, 2 bytes padding
        int written = Core.encodeIntoSlice(text, buffer, 0, 5, encoder, (byte) '_');
        assertEquals(3, written, "Only the 3-byte emoji should have been written");
        // Validate buffer contents
        byte[] data = buffer.array();
        // First 3 bytes should be the Sparkles emoji
        assertEquals((byte) 0xE2, data[0]);
        assertEquals((byte) 0x9C, data[1]);
        assertEquals((byte) 0xA8, data[2]);
        // Last 2 bytes should be padding because 😀 didn't fit
        assertEquals((byte) '_', data[3]);
        assertEquals((byte) '_', data[4]);
        assertEquals(0, buffer.position(), "State should be restored");
    }
}
