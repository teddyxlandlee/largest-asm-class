# Largest ASM Class
This code generates the largest class that ASM can generate, whose size is
`2_147_483_645` bytes, the maximum size of a JVM array.

## Information of generated class

Generated class is executable. It does nothing but prints an empty line to
stdout (after a long init phase).

Generated class (FQCN: `Code`), version 69.0 (requires Java 25 or later),
is a public interface, which has:
- no fields;
- a sole method `public static synthetic void main()` that contains 65531 NOPs, an
  `IO.println()` `invokestatic` call, and a `return` opcode;
- 16 attributes with long attribute names (65535 ascii characters for each), each of
  which is filled with 134,148,079 (`0x07FE_EFEF`) NULs; and
- An orphan utf8 constant `UUUUUU` (`U` * 6), to fill the class file to the maximum size.

SHA-256 of `Code.class` (after decompression):
`984990b558cc9e1d64a31ed0a4e54f5326b68ba8d103c61e25fd43356829310f`

## Build environment
`ASMGenAttributedClass.java` is compiled and run on Java 21, with `org.ow2.asm:asm:9.9.1`
on classpath.
