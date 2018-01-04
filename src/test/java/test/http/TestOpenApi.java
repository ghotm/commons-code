package test.http;

import java.util.Map;
import java.util.Random;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import code.ponfee.commons.http.Http;
import code.ponfee.commons.json.Jsons;
import code.ponfee.commons.util.ObjectUtils;
import code.ponfee.commons.util.Strings;

@SuppressWarnings("unchecked")
public class TestOpenApi {
    //String host = "http://10.202.64.167:8080";
    String host = "http://10.118.58.74:8000";
    @Test
    public void test1() {
        Object data = ImmutableMap.of("head", ImmutableMap.of("app_id", "FQ", "trans_id", ObjectUtils.uuid22()), 
                                      "body", ImmutableMap.of("username", "rnT2s9YMKGHkannrr-kNFSZYk-XOLBRKIIjnY62szVR0E31j8SQx50cym_KVXT5m1yjzcHUHh-M8g24q-oHnPa-MgaNaKR8IK-TUa9I9S9Vo8Xseg25EjgrvONK2X4ahKbSBHqrJfyIrPmE7rO70XHnhhma5HtLLXVukhY7eVJk", 
                                                              "password", "m6bZQrh72pkbBXBmvA-OoSSYRDDtht4mnH4c9VZ77dPnE6qbD8mMJPC_1Wn6Dhr28IfkSv2o3bS44zraBVZ0b-wxMRJ9meZ9i7Vvkwab2uL-eJOvYQ25xjHbdEinsdEyb7rMq0u8Flw9_KqmojEPQVJvSQyV_fJFin8Ns6p_li4"));

        Map<String, Object> resp = Http.post(host+"/open/api/auth")
                                       .contentType("application/json", "UTF-8") 
                                       .data(Jsons.NORMAL.stringify(data))
                                       .accept("application/json") 
                                       .request(Map.class);
        System.out.println(resp);
    }

    @Test
    public void test2() {
        Object data = ImmutableMap.of("head", ImmutableMap.of("app_id", "FQ", "trans_id", ObjectUtils.uuid22()), 
                                      "body", ImmutableMap.of("username", "rnT2s9YMKGHkannrr-kNFSZYk-XOLBRKIIjnY62szVR0E31j8SQx50cym_KVXT5m1yjzcHUHh-M8g24q-oHnPa-MgaNaKR8IK-TUa9I9S9Vo8Xseg25EjgrvONK2X4ahKbSBHqrJfyIrPmE7rO70XHnhhma5HtLLXVukhY7eVJk"));

        Map<String, Object> resp = Http.post(host+"/open/api/userinfo")
                                       .contentType("application/json", "UTF-8") 
                                       .data(Jsons.NORMAL.stringify(data))
                                       .accept("application/json") 
                                       .request(Map.class);
        System.out.println(resp);
    }

    @Test
    public void test3() {
        System.out.println(Http.post(host + "/open/api/test?a=1=32=14=12=4=3214=2&abcdef&" + Math.random()).request());
    }

    public static void main(String[] args) {
        String captcha = "1234";
        long number = new Random(captcha.hashCode()).nextLong();
        System.out.println(number);
        System.out.println(captcha.hashCode());
        number = Strings.crc32(Integer.toString(captcha.hashCode()));
        System.out.println(number);
    }
}
