import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClassWithErrors {

    private static final String MESSAGE_TEMPLATE = """
        Row 1 %s
        Row 2
        """;

    public static void main(final String[] args) {
        System.out.println(String.format(MESSAGE_TEMPLATE, "1"));

            String regex = args[0];
        String content = args[1];

        Pattern pat = Pattern.compile(regex);
        Matcher mat = pat.matcher(content);

        if (mat.matches()) {
            System.out.println("Group 1: " + mat.group(1));
        } else {
            System.out.println("No match!");
        }
    }

}