package dev.optimuslink.core;

public final class LegacyPageReceiptsTest {
    private static int assertions;
    private static void check(boolean condition,String message) { assertions++;if(!condition)throw new AssertionError(message); }
    public static void main(String[] args) {
        var pages=new LegacyPageReceipts<Object>();var body=new Object();var actor=new Object();
        pages.customPage(body,true);check(pages.visible()==body,"body roster is visible");
        check(pages.acknowledge()==body,"initial roster acknowledgement belongs to body");
        pages.customPage(body,false);pages.setPage();
        pages.customPage(actor,true);pages.customPage(actor,false);
        check(pages.acknowledge()==body,"late roster update remains on body after assume");
        check(pages.acknowledge()==body,"roster close acknowledgement remains on body after assume");
        check(pages.acknowledge()==actor,"actor initial page has its own acknowledgement");
        pages.setPage(); // Release while an actor update is still awaiting acknowledgement.
        pages.customPage(body,true);
        check(pages.acknowledge()==actor,"retired actor update cannot reach the new body page");
        check(pages.acknowledge()==actor,"retired actor close cannot reach the new body page");
        check(pages.acknowledge()==body,"body page acknowledgement survives actor retirement");
        check(pages.acknowledge()==null,"all expected acknowledgements drain exactly once");
        pages.dismiss(actor);check(pages.visible()==body,"late actor dismissal cannot clear a new body page");
        pages.dismiss(body);check(pages.visible()==null,"client dismissal clears the visible page");
        pages.setPage();check(pages.acknowledge()==null,"closing a dismissed page has no legacy receipt");
        pages.customPage(actor,true);pages.clear();check(pages.visible()==null&&pages.acknowledge()==null,"disconnect discards the entire connection ledger");
        System.out.println("Legacy page ownership: "+assertions+" assertions passed");
    }
}
