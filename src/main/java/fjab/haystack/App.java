package fjab.haystack;

/**
 * Hello world!
 *
 */
public class App
{
    public static boolean testMode = true;
    public static void main( String[] args ) {
        testMode = Boolean.parseBoolean(args[0]);
        System.out.println("testMode: " + testMode);
    }
}
