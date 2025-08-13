package burp.RawHexler;

import java.awt.*;
import java.awt.datatransfer.*;

public class ClipboardUtility {

    public static void copyToClipboard(String text) {
        // Wrap the text in a Transferable
        StringSelection selection = new StringSelection(text);
        // Get the system clipboard (works on Windows, macOS, Linux with X11/Wayland)
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        // Put the content on the clipboard
        clipboard.setContents(selection, null);
    }

    /** Get the current clipboard contents as a string, or null if it isn’t plain text. */
    public static String getClipboardText() {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        try {
            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                return (String) clipboard.getData(DataFlavor.stringFlavor);
            }
        } catch (UnsupportedFlavorException | java.io.IOException ex) {
            // ignore or log as needed
        }
        return null;   // not text, or error
    }

}

