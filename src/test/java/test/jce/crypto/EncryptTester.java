package test.jce.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import code.ponfee.commons.jce.crypto.Algorithm;
import code.ponfee.commons.jce.crypto.Mode;
import code.ponfee.commons.jce.crypto.Padding;
import code.ponfee.commons.jce.crypto.SymmetricCryptor;
import code.ponfee.commons.jce.crypto.SymmetricCryptorBuilder;
import code.ponfee.commons.util.Bytes;
import code.ponfee.commons.util.SecureRandoms;

public class EncryptTester {

    public static void main(String[] args) {
        BouncyCastleProvider bc = new BouncyCastleProvider();
        SymmetricCryptor coder = null;

        //coder = EncryptorBuilder.newBuilder(Algorithm.DESede).build();
        //coder = EncryptorBuilder.newBuilder(Algorithm.RC2).key(randomBytes(5)).build();
        //coder = EncryptorBuilder.newBuilder(Algorithm.RC2).key(randomBytes(16)).mode(Mode.ECB).padding(Padding.NoPadding).provider(bc).build();
        coder = SymmetricCryptorBuilder.newBuilder(Algorithm.TDEA).key(SecureRandoms.nextBytes(16)).provider(bc).build();
        //coder = EncryptorBuilder.newBuilder(Algorithm.AES).key(randomBytes(16)).build();
        //coder = EncryptorBuilder.newBuilder(Algorithm.AES).key(randomBytes(16)).mode(Mode.ECB).padding(Padding.PKCS5Padding).provider(bc).build();
        //coder = EncryptorBuilder.newBuilder(Algorithm.AES).key(randomBytes(16)).mode(Mode.OFB).padding(Padding.NoPadding).ivParameter(randomBytes(16)).provider(bc).build();
        //coder = EncryptorBuilder.newBuilder(Algorithm.AES).key(randomBytes(16)).mode(Mode.CBC).padding(Padding.PKCS7Padding).ivParameter(randomBytes(16)).provider(bc).build();
        //coder = EncryptorBuilder.newBuilder(Algorithm.DES).key(randomBytes(8)).mode(Mode.CBC).padding(Padding.NoPadding).ivParameter(randomBytes(8)).provider(bc).build();
        //coder = EncryptorBuilder.newBuilder(Algorithm.DES).key(randomBytes(8)).provider(bc).build();
        //coder = EncryptorBuilder.newBuilder(Algorithm.DES).key(randomBytes(8)).mode(Mode.CBC).padding(Padding.PKCS5Padding).ivParameter(randomBytes(8)).provider(bc).build();
        //coder = EncryptorBuilder.newBuilder(Algorithm.DESede).key(randomBytes(16)).provider(bc).build();
        //coder = EncryptorBuilder.newBuilder(Algorithm.DESede).key(randomBytes(24)).mode(Mode.CBC).padding(Padding.PKCS5Padding).ivParameter(randomBytes(8)).build();
        //coder = EncryptorBuilder.newBuilder(Algorithm.DESede).key(Bytes.randomBytes(16)).mode(Mode.ECB).padding(Padding.PKCS5Padding).provider(bc).build();
        //coder = SymmetricCryptorBuilder.newBuilder(Algorithm.DESede).key(SecureRandoms.nextBytes(16)).mode(Mode.CBC).padding(Padding.PKCS5Padding).ivParameter(SecureRandoms.nextBytes(8)).provider(bc).build();

        byte[] encrypted = coder.encrypt("12345678".getBytes()); // 加密
        byte[] origin = coder.decrypt(encrypted); // 解密
        System.out.println(new String(origin));
    }
}
