import cn.hutool.crypto.digest.BCrypt;

public class GenPassword {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("用法: java GenPassword <密码>");
            System.exit(1);
        }
        String password = args[0];
        System.out.println(BCrypt.hashpw(password, BCrypt.gensalt(12)));
    }
}
