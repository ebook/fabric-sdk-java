package handler;

/**
 * run this main method and check result 
 */
public class Main {
    public static void main(String args[]) throws Exception {
        HandlerHelper helper = new HandlerHelper();
        helper.init();
        helper.newChannel();
    
        helper.invoke(new String[]{"a", "b", "10"});
        System.out.println("a:"+helper.query(new String[]{"a"}));
        System.out.println("b:"+helper.query(new String[]{"b"}));
    }
}
