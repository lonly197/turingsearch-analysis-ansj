package cn.com.turingsearch.util;

import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.Key;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by Lonly on 2016/11/29.
 */
public class FileDeEncrypt {
    public static ESLogger logger = Loggers.getLogger(FileDeEncrypt.class);


    public FileDeEncrypt(String keyStr) {
        genKey(keyStr);
        initCipher();
    }

    private Key key;
    // 解密密码
    private Cipher cipherDecrypt;
    // 加密密码
    private Cipher cipherEncrypt;


    /**
     * 加密文件的核心
     *
     * @param srcFileName
     *            要加密的文件
     * @param destFileName
     *            加密后存放的文件名
     */
    public boolean encryptFile(String srcFileName, String destFileName) {
        try {
            InputStream is = new FileInputStream(srcFileName);
            OutputStream out = new FileOutputStream(destFileName);

            CipherInputStream cis = new CipherInputStream(is, cipherEncrypt);
            byte[] buffer = new byte[1024];
            int r;
            while ((r = cis.read(buffer)) > 0) {
                out.write(buffer, 0, r);
            }
            cis.close();
            is.close();
            out.close();
            logger.info("文件{}加密完成，加密后的文件是:{}", srcFileName, destFileName);
            return true;
        }
        catch (Exception e) {
            logger.error("加密文件{}出现异常", srcFileName, e.getMessage());
            e.printStackTrace();
            return false;
        }
    }


    /***
     * 解密文件
     *
     * @param fileName
     */
    public List<String> decryptFileContent(String fileName) {
        List<String> list = new ArrayList<String>();
        try {
            InputStream is = new FileInputStream(fileName);
            CipherInputStream cis = new CipherInputStream(is, cipherDecrypt);
            BufferedReader reader = new BufferedReader(new InputStreamReader(cis));
            String line = null;
            while ((line = reader.readLine()) != null) {
                list.add(line);
            }
            reader.close();
            cis.close();
            is.close();
            logger.info("文件{}解密完成", fileName);
        }
        catch (Exception e) {
            logger.error("解密文件{}出现异常", fileName, e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 解密文件，返回reader
     * @param fileName
     * @return
     */
    public BufferedReader decryptFile(String fileName) {
        BufferedReader reader = null;
        try {
            InputStream is = new FileInputStream(fileName);
            CipherInputStream cis = new CipherInputStream(is, cipherDecrypt);
            reader = new BufferedReader(new InputStreamReader(cis,"UTF-8"));
        }
        catch (Exception e) {
            logger.error("解密文件{}出现异常", fileName, e.getMessage());
            e.printStackTrace();
        }
        return reader;
    }


    private void initCipher() {
        try {
            // 加密的cipher
            cipherEncrypt = Cipher.getInstance("DES");
            cipherEncrypt.init(Cipher.ENCRYPT_MODE, key);
            // 解密的cipher
            cipherDecrypt = Cipher.getInstance("DES");
            cipherDecrypt.init(Cipher.DECRYPT_MODE, key);
        }
        catch (Exception e) {
            logger.info("加密初始化出现异常:{}", e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * 自定义一个key
     *
     * @param keyRule
     */
    public void genKey(String keyRule) {
        // Key key = null;
        byte[] keyByte = keyRule.getBytes();
        // 创建一个空的八位数组,默认情况下为0
        byte[] byteTemp = new byte[8];
        // 将用户指定的规则转换成八位数组
        for (int i = 0; i < byteTemp.length && i < keyByte.length; i++) {
            byteTemp[i] = keyByte[i];
        }
        key = new SecretKeySpec(byteTemp, "DES");
    }


    /***
     * 测试加密解密
     *
     * @param args
     */
    public static void main(String[] args) {
        FileDeEncrypt deEncrypt = new FileDeEncrypt("STP FILE DE-ENCRYPT");
        deEncrypt.encryptFile("src/main/dic/user/userDefine.dic", "encrypt/user/userDefine.dic");
//        List<String> list = deEncrypt.decryptFileContent("pom.bin");
//        for (String str : list) {
//            System.out.println(str);
//        }
    }
}
