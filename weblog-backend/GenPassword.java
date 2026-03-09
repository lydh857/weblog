import cn.hutool.crypto.digest.BCrypt;

public class GenPassword {
    public static void main(String[] args) {
        String password = args.length > 0 ? args[0] : "zkldh666@Z";
        System.out.println(BCrypt.hashpw(password, BCrypt.gensalt(12)));
    }
}
