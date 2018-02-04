package test.jce.rsa;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import org.junit.Test;

import com.google.common.base.Stopwatch;

import code.ponfee.commons.jce.rsa.RSAKey;
import code.ponfee.commons.jce.rsa.RSASigner;
import code.ponfee.commons.jce.security.RSACryptor;
import code.ponfee.commons.jce.security.RSAPrivateKeys;
import code.ponfee.commons.jce.security.RSAPublicKeys;
import code.ponfee.commons.util.IdcardResolver;
import code.ponfee.commons.util.MavenProjects;

public class RSASignerTest {

    private static byte[] origin = MavenProjects.getMainJavaFileAsByteArray(IdcardResolver.class);

    @Test
    public void testRSASign() throws Exception {
        RSAKey dk = new RSAKey(1024);
        RSAKey ek = (RSAKey) dk.getPublic();
        RSAPublicKey pub = RSAPublicKeys.toRSAPublicKey(dk.n, dk.e);
        RSAPrivateKey pri = RSAPrivateKeys.toRSAPrivateKey(dk.n, dk.d);

        // ＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝验证签名
        Stopwatch watch = Stopwatch.createStarted();
        byte[] signature = new RSASigner(dk).signSha1(origin); // 签名

        System.out.println(new RSASigner(ek).verifySha1(origin, signature)); // 验证
        System.out.println(RSACryptor.verifySha1(origin, pub, signature)); // 验证

        // ＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝验证签名
        signature = RSACryptor.signSha1(origin, pri); // 签名
        System.out.println(RSACryptor.verifySha1(origin, pub, signature)); // 验证
        System.out.println(new RSASigner(ek).verifySha1(origin, signature)); // 验证

        System.out.println(watch.stop());
    }

}