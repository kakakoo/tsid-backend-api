package com.tsid.api.util;

import com.tsid.api.exception.ErrorCode;
import com.tsid.api.exception.TSIDServerException;
import com.tsid.domain.enums.EErrorActionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

@Component
@Slf4j
public class EncryptUtil {

    private static String RSA_PRIVATE_KEY;

    @Value("${rsa.private.key}")
    public void setRsaPrivateKey(String key){
        this.RSA_PRIVATE_KEY = key;
    }

    public static String sha256Encrypt(String text) {

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(text.getBytes());

            return sha256BytesToHex(md.digest());
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new TSIDServerException(ErrorCode.INTERNAL_SERVER_ERROR_ENCRYPT, EErrorActionType.NONE, "암호화 오류");
        }
    }

    private static String sha256BytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    public static String aes256Encrypt(String key, String data) {
        String alg = "AES/CBC/PKCS5Padding";

        String iv = key.substring(0, 16); //16byte

        try {
            Cipher cipher = Cipher.getInstance(alg);

            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), "AES");
            IvParameterSpec ivParamSpec = new IvParameterSpec(iv.getBytes());
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivParamSpec);

            byte[] encrypted1 = cipher.doFinal(data.getBytes("UTF-8"));

            return Base64.getUrlEncoder().encodeToString(encrypted1);

        }catch (Exception e) {
            log.error(e.getMessage());
            throw new TSIDServerException(ErrorCode.INTERNAL_SERVER_ERROR_ENCRYPT, EErrorActionType.NONE, "암호화 오류");
        }
    }

    public static String aes256Decrypt(String key, String data) {
        String alg = "AES/CBC/PKCS5Padding";

        String iv = key.substring(0, 16); //16byte

        try {
            Cipher cipher = Cipher.getInstance(alg);
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), "AES");

            IvParameterSpec ivParamSpec = new IvParameterSpec(iv.getBytes());
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivParamSpec);

            byte[] decodedBytes = Base64.getUrlDecoder().decode(data);
            byte[] decrypted = cipher.doFinal(decodedBytes);

            return new String(decrypted, "UTF-8");
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new TSIDServerException(ErrorCode.INTERNAL_SERVER_ERROR_ENCRYPT, EErrorActionType.NONE, "복호화 오류");
        }
    }

    /**
     * RSA 복호화
     */
    public static String rsaDecrpyt(String data){
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            byte[] bytePrivateKey = Base64.getDecoder().decode(RSA_PRIVATE_KEY.getBytes());
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(bytePrivateKey);
            PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);

            byte[] byteEncryptedData = Base64.getDecoder().decode(data.getBytes("UTF-8"));
            byte[] byteDecryptedData = cipher.doFinal(byteEncryptedData);

            return new String(byteDecryptedData);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new TSIDServerException(ErrorCode.INTERNAL_SERVER_ERROR_ENCRYPT, EErrorActionType.NONE, "RSA 복호화 오류");
        }
    }


}