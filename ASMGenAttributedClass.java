import org.objectweb.asm.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ASMGenAttributedClass implements Opcodes {
    public static void main(String[] args) throws IOException {
        final Path path = Path.of(args[0]);
        ClassWriter cw = new ClassWriter(0);
        genClass(cw);
        // Current size: 2147483636, still 11 bytes left
        // However, VM limit is at (Integer.MAX_VALUE - 2)
        // 1 tag, 2 size, 6 utfData
        cw.newUTF8("U".repeat(6));

        try (var output = Files.newOutputStream(path)) {
            output.write(cw.toByteArray());
        }
    }

    private static void genClass(ClassVisitor cv) {
        MethodVisitor mv;

        cv.visit(V25, ACC_INTERFACE | ACC_PUBLIC | ACC_ABSTRACT, "Code", null, "java/lang/Object", null);
        // main()V
        {
            mv = cv.visitMethod(ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC, "main", "()V", null, null);
            mv.visitCode();
            for (int i = 0; i < (65534 - 3); i++) mv.visitInsn(NOP);
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/IO", "println", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // used constant pool entries:
        // Code, C(Code), java/lang/Object, C(java/lang/Object),
        // main, ()V, java/lang/IO, C(java/lang/IO),
        // println, N(println:()V), Mr(java/lang/IO.println:()V)
        // Total: 11 entries
//        final int remainingCpCount = 0xFFFE - 11;
        final int remainingCpCount = 16;
        final int attributeSize = 0x07FE_EFEF;

        var stringFactory = HugeStringFactory.newDefaultIterable(65535, remainingCpCount);
        for (String name: stringFactory) {
            cv.visitAttribute(new HugeNulAttribute(name, attributeSize));
        }
        cv.visitEnd();
    }

    private static class HugeNulAttribute extends Attribute {
        private final int size;

        HugeNulAttribute(String type, int size) {
            super(type);
            this.size = size;
        }

        @Override
        protected ByteVector write(ClassWriter classWriter, byte[] code, int codeLength, int maxStack, int maxLocals) {
            return write();
        }

        private ByteVector write() {
            ByteVector b = new ByteVector(size);
            final int bufferSize = 1048576;
            byte[] buffer = new byte[bufferSize];  // automatically set nul
            for (int i = size; i > 0; i -= bufferSize) {
                b.putByteArray(buffer, 0, Math.min(i, bufferSize));
            }
            return b;
        }
    }
}

class HugeStringFactory implements Iterator<String> {
    public static final char SPECIAL_CHAR = 0b0101_0101;    // 'U'

    public static Iterable<String> newIterable(char baseChar, int maxLength, int iterateCount) {
        if (maxLength <= 0) throw new IllegalArgumentException("maxLength must be greater than 0");
        if (iterateCount < 0) throw new IllegalArgumentException("iterateCount must not be negative");
        if (iterateCount == 0) return Collections.emptySet();
        if (iterateCount == 1) {
            // requires maxLength >= 2
            char[] arr = new char[maxLength];
            Arrays.fill(arr, baseChar);
            arr[maxLength - 1] = 'A';
            arr[maxLength - 2] = 'A';
            return Collections.singleton(String.valueOf(arr));
        }
        return () -> new HugeStringFactory(baseChar, maxLength, iterateCount);
    }

    public static Iterable<String> newDefaultIterable(int maxLength, int iterateCount) {
        return newIterable(SPECIAL_CHAR, maxLength, iterateCount);
    }

    // Iterate range: URLSafe_Base64 without padding
    public static final Base64.Encoder NO_PADDING_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final byte[] src;   // big endian
    private final byte[] dst;
    private final int maxLength;
    private final char[] baseCharCache;
    private boolean hasNext = true;

    private HugeStringFactory(char baseChar, int maxLength, int iterateCount) {
        if (iterateCount <= 1) {
            throw new IllegalArgumentException("iterateCount must be greater than 1");
        }
        int maxVal = iterateCount - 1;

        final int srcLen = (39 - Integer.numberOfLeadingZeros(maxVal)) >>> 3;
        final int dstLen = (srcLen * 4 + 2) / 3;
        if (dstLen > maxLength) {
            throw new IllegalArgumentException("maxLength too short (dstLen=" + dstLen + ", maxLength=" + maxLength + ")");
        }

        this.maxLength = maxLength;
        src = new byte[srcLen];
        dst = new byte[dstLen];
        baseCharCache = new char[maxLength - dstLen];
        Arrays.fill(baseCharCache, baseChar);

        for (int i = srcLen - 1; i >= 0; i--) {  // big endian
            src[i] = (byte) maxVal;
            maxVal >>>= 8;
        }
    }

    @Override
    public boolean hasNext() {
        return hasNext;
    }

    @Override
    public String next() {
        if (!hasNext) throw new NoSuchElementException();
        String ret = currentString();
        iterate();
        return ret;
    }

    private String currentString() {
        NO_PADDING_ENCODER.encode(src, dst);
        var sb = new StringBuilder(maxLength);
        sb.append(baseCharCache);
        sb.append(new String(dst, StandardCharsets.ISO_8859_1));
        return sb.toString();
    }

    private void iterate() {
        for (int i = src.length - 1; i >= 0; i--) {
            if (src[i]-- != 0) {
                return;
            }
        }
        // Was all zero
        hasNext = false;
    }
}

