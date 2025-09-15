package burp.RawHexler;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.RawEditor;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.EditorMode;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpResponseEditor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.nio.file.Files;


import static burp.api.montoya.core.ByteArray.byteArray;

public class RawHexlerEntensionProvidedEditor implements ExtensionProvidedHttpRequestEditor, ExtensionProvidedHttpResponseEditor {
    private final MontoyaApi api;
    private final RawHexlerHttpEditorProvider rawHexlerHttpEditorProvider;
    private RawEditor rawEditor;
    private HttpRequest  editedRequest  = null;
    private HttpResponse editedResponse = null;
    private HttpRequestResponse requestResponse;
    private boolean dirtyCheck = false;

    private JMenuItem menuItemPrefixRow, menuItemSpaceDelim, menuItemUtf8Postfix, menuItemAddFile, fileToSearch, refreshFormat, pasteClipboard;

    public RawHexlerEntensionProvidedEditor(MontoyaApi api, EditorCreationContext editorCreationContext, RawHexlerHttpEditorProvider rawHexlerHttpEditorProvider) {
        this.api = api;
        this.rawHexlerHttpEditorProvider = rawHexlerHttpEditorProvider;


        if (editorCreationContext.editorMode() == EditorMode.READ_ONLY)
        {
            rawEditor = api.userInterface().createRawEditor(EditorOptions.READ_ONLY);
        }
        else {
            rawEditor = api.userInterface().createRawEditor();
        }

        JPopupMenu popupMenu = createPopupMenu();
        rawEditor.uiComponent().addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (SwingUtilities.isRightMouseButton(e))
                {
                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

    }



    private JPopupMenu createPopupMenu() {
        JPopupMenu popupMenu = new JPopupMenu();

        menuItemPrefixRow = new JMenuItem("Toggle Prefix Offset");
        menuItemPrefixRow.addActionListener(e -> {
            rawHexlerHttpEditorProvider.setPrefixRowCount(!rawHexlerHttpEditorProvider.isPrefixRowCount());
            refreshEditor();
        });
        popupMenu.add(menuItemPrefixRow);

        fileToSearch = new JMenuItem("hex-File to Clipboard");
        fileToSearch.addActionListener(e -> {
            SwingUtilities.invokeLater(() -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Select a file to import");
                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                chooser.setDragEnabled(true);
                int result = chooser.showOpenDialog(null);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File selected = chooser.getSelectedFile();
                    this.hexFileToClipboard(selected);

                }
            });
        });
        popupMenu.add(fileToSearch);

        menuItemSpaceDelim = new JMenuItem("Toggle Space Delimiters");
        menuItemSpaceDelim.addActionListener(e -> {

            rawHexlerHttpEditorProvider.setSpaceDelimiters(!rawHexlerHttpEditorProvider.isSpaceDelimiters());
            refreshEditor();
        });
        popupMenu.add(menuItemSpaceDelim);

        menuItemUtf8Postfix = new JMenuItem("Toggle UTF-8 PostFix notation");
        menuItemUtf8Postfix.addActionListener(e -> {
            rawHexlerHttpEditorProvider.setPostfixUTF8(!rawHexlerHttpEditorProvider.isPostfixUTF8());
            refreshEditor();
        });
        popupMenu.add(menuItemUtf8Postfix);

        refreshFormat = new JMenuItem("Reset");
        refreshFormat.addActionListener(e -> {
            refreshEditor();
        });
        popupMenu.add(refreshFormat);


        menuItemAddFile = new JMenuItem("Paste from hex File");
        menuItemAddFile.addActionListener(e -> {
            // Always invoke GUI code on the EDT for safety in Burp
            SwingUtilities.invokeLater(() -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Select a file to import");
                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                chooser.setDragEnabled(true);        // enables drag‑and‑drop
                int result = chooser.showOpenDialog(null);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File selected = chooser.getSelectedFile();
                    this.addFileFromPath(selected); // overload takes File

                }
            });
        });
        popupMenu.add(menuItemAddFile);

        pasteClipboard = new JMenuItem("Paste hex from Clipboard");
        pasteClipboard.addActionListener(e -> {
            this.pasteHex();

        });
        popupMenu.add(pasteClipboard);



        return popupMenu;
    }



    @Override
    public HttpRequest getRequest() {
        // If user edited, build from editor now
        if (rawEditor.isModified() || dirtyCheck) {
            try {
                byte[] bytes = hexToBytes(rawEditor.getContents().toString());

                return HttpRequest.httpRequest(ByteArray.byteArray(bytes));
            } catch (Exception ex) {
                api.logging().logToError("Invalid hex in editor: " + ex);
                // Fall back to last good version:
                return (editedRequest != null) ? editedRequest : requestResponse.request();
            }
        }
        // Otherwise return last committed or original
        return (editedRequest != null) ? editedRequest : requestResponse.request();
    }

    @Override
    public HttpResponse getResponse() {
        if (rawEditor.isModified() || dirtyCheck) {
            try {
                byte[] bytes = hexToBytes(rawEditor.getContents().toString());
                return HttpResponse.httpResponse(ByteArray.byteArray(bytes));
            } catch (Exception ex) {
                api.logging().logToError("Invalid hex in editor: " + ex);
                return (editedResponse != null) ? editedResponse : requestResponse.response();
            }
        }
        return (editedResponse != null) ? editedResponse : requestResponse.response();
    }

    @Override
    public void setRequestResponse(HttpRequestResponse httpRequestResponse) {
        this.requestResponse = httpRequestResponse;
        this.editedRequest = null;
        this.editedResponse = null;
        dirtyCheck=false;
        refreshEditor();
    }

    @Override
    public boolean isEnabledFor(HttpRequestResponse httpRequestResponse) {
        return true;
    }

    @Override
    public String caption() {
        return "RawHex-ler";
    }

    @Override
    public Component uiComponent() {
        return rawEditor.uiComponent();
    }

    @Override
    public Selection selectedData() {
        return rawEditor.selection().isPresent() ? rawEditor.selection().get() : null;
    }

    @Override
    public boolean isModified() {
        return dirtyCheck || rawEditor.isModified();
    }





    private void save() {
        byte[] bytes;
        try {
            bytes = hexToBytes(rawEditor.getContents().toString());
        } catch (Exception ex) {
            api.logging().logToError("Reload failed - invalid hex: " + ex);
            return;
        }

        if (rawHexlerHttpEditorProvider.isRequestMode()) {
            editedRequest = HttpRequest.httpRequest(ByteArray.byteArray(bytes));
        } else {
            editedResponse = HttpResponse.httpResponse(ByteArray.byteArray(bytes));
        }
        dirtyCheck = true;

    }

    private void hexFileToClipboard(File file) {
        // 0) Read file -> hex text
        //final String hexFile;
        final String hexFileSearch;
        try {
            //hexFile=bytesToHex(Files.readAllBytes(file.toPath()));
            if(rawHexlerHttpEditorProvider.isSpaceDelimiters()){
                hexFileSearch = bytesToHexClip(Files.readAllBytes(file.toPath()), true);}
            else{
                hexFileSearch = bytesToHexClip(Files.readAllBytes(file.toPath()), false);
            }

        } catch (IOException ex) {
            api.logging().logToError("read file failed: " + ex);
            return;
        }


        ClipboardUtility.copyToClipboard(hexFileSearch);
        rawEditor.setSearchExpression(hexFileSearch);

        dirtyCheck = true;
    }

    private void pasteHex() {

        String hexFile=ClipboardUtility.getClipboardText();
        // 1) Current editor text AS-IS (already hex text)
        String currentHex = rawEditor.getContents().toString();

        // 2) Caret returned by RawEditor is a byte offset into the SAME text you gave it.
        int caret = rawEditor.caretPosition();
        if (caret < 0) caret = 0;
        int insertAt = Math.min(caret, currentHex.length());

        // 3) Insert
        String newHex = new StringBuilder(currentHex)
                .insert(insertAt, hexFile)
                .toString();

        // 4) Push HEX TEXT back
        rawEditor.setContents(ByteArray.byteArray(newHex));


        this.save();
        this.refreshEditor();
    }

    private void addFileFromPath(File file) {
        // 0) Read file -> hex text
        final String hexFile;
        final String hexFileSearch;
        try {
            hexFile = bytesToHex(Files.readAllBytes(file.toPath()));
            if(rawHexlerHttpEditorProvider.isSpaceDelimiters()){
                hexFileSearch = bytesToHexClip(Files.readAllBytes(file.toPath()), true);}
            else{
                hexFileSearch = bytesToHexClip(Files.readAllBytes(file.toPath()), false);
            }
        } catch (IOException ex) {
            api.logging().logToError("read file failed: " + ex);
            return;
        }

        // 1) Current editor text AS-IS (already hex text)
        String currentHex = rawEditor.getContents().toString();

        // 2) Caret returned by RawEditor is a byte offset into the SAME text you gave it.
        int caret = rawEditor.caretPosition();
        if (caret < 0) caret = 0;
        int insertAt = Math.min(caret, currentHex.length());

        // 3) Insert
        String newHex = new StringBuilder(currentHex)
                .insert(insertAt, hexFile)
                .toString();

        // 4) Push HEX TEXT back
        rawEditor.setContents(ByteArray.byteArray(newHex));


        // 6) Optional highlight
        rawEditor.setSearchExpression(hexFileSearch);



        this.save();
        this.refreshEditor();
    }






    private void refreshEditor(){
        String hexRepresentation;
        if(rawHexlerHttpEditorProvider.isRequestMode()){
            if(dirtyCheck){
                hexRepresentation = bytesToHex(this.editedRequest.toByteArray().getBytes());
            }
            else{
                hexRepresentation = bytesToHex(requestResponse.request().toByteArray().getBytes());
            }
        }else{
            if(dirtyCheck){
                hexRepresentation = bytesToHex(this.editedResponse.toByteArray().getBytes());
            }
            else{
                hexRepresentation = bytesToHex(requestResponse.response().toByteArray().getBytes());
            }
        }
        ByteArray output = byteArray(hexRepresentation);
        this.rawEditor.setContents(output);
        this.rawEditor.uiComponent();
    }


    // Helper method to convert hex string to byte array for RawHexler formatted content
    private byte[] hexToBytes(String hexString) {
        if (rawHexlerHttpEditorProvider.isPrefixRowCount()){
            String[] lines = hexString.split("\n");
            StringBuilder result = new StringBuilder();
            // Iterate over each line and remove the first 10 characters
            for (String line : lines) {
                if (line.length() > 10) {
                    result.append(line.substring(10)); // Remove the first 10 characters
                }
                result.append("\n"); // Add a newline character after each line
            }

            // Convert StringBuilder to a String and remove the trailing newline
            hexString = result.toString().trim();

        }
        if (rawHexlerHttpEditorProvider.isPostfixUTF8()){
            String[] lines = hexString.split("\n");
            StringBuilder result = new StringBuilder();
            for (String line : lines) {
                String[] hexUtf8Array = line.split("\\s{3}"); // Split string among 3 spaces in row as separator
                String eosChar = (rawHexlerHttpEditorProvider.isSpaceDelimiters())? " --" : "--";
                result.append(hexUtf8Array[0].replace(eosChar,"")); // Only take first array element as this contains the hex values and remove -- hex place holder (normally only occurring at the end of a string a placeholders)
            }
            hexString = result.toString().trim();
        }else{
            //hexString=hexString.replace("\n","");
            String[] lines = hexString.split("\n");
            StringBuilder result = new StringBuilder();
            for (String line : lines) {
                result.append(line);
            }
            hexString = result.toString().trim();
        }
        if (rawHexlerHttpEditorProvider.isSpaceDelimiters()) {
            hexString = hexString.replaceAll("\\s+", "");
        }
        if (rawHexlerHttpEditorProvider.isFixedWidth()){
            hexString = hexString.replaceAll("\\n", "");
        }

        int length = hexString.length();
        byte[] byteArray = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            byteArray[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i+1), 16));
        }
        return byteArray;
    }


    private static String bytesToHexClip(byte[] data, boolean spaces) {
        StringBuilder sb = new StringBuilder(data.length * 3 - 1);  // “ff ” per byte
        for (int i = 0; i < data.length; i++) {
            sb.append(String.format("%02x", data[i]));
            if (spaces){
                if (i < data.length - 1) sb.append(' ');
            }

        }
        return sb.toString();
    }


    // Helper method to convert byte array to hex string for RawHexler formatted content
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        StringBuilder rawUTF8String = new StringBuilder();
        int count = 1;
        for (byte b : bytes) {
            if (rawHexlerHttpEditorProvider.isPrefixRowCount()){
                if (((count % 16)==1)){
                    String hexRowString = String.format("%08x",count-1);
                    hexString.append(hexRowString + "  ");
                }
            }
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
            //Append space between bytes (only inner bytes, not EOL)
            if (rawHexlerHttpEditorProvider.isSpaceDelimiters() && !(count == bytes.length) && !((count % 16)==0)) {
                hexString.append(" ");
            }
            if (rawHexlerHttpEditorProvider.isPostfixUTF8()){
                if (b != 10 && b != 13) {
                    rawUTF8String.append((char)b);
                } else {
                    rawUTF8String.append(" ");
                }
                if (count == bytes.length) {
                    String eosChar = (rawHexlerHttpEditorProvider.isSpaceDelimiters())? " --" : "--";
                    hexString.append(eosChar.repeat((16-(count%16))-(16*((16-(count%16))/16))));
                    if((count % 16)!=0){
                        hexString.append("   ");
                        hexString.append(rawUTF8String);
                    }
                }

                if((count % 16)==0){
                    hexString.append("   ");
                    hexString.append(rawUTF8String);
                    rawUTF8String = new StringBuilder();
                }
            }
            if (rawHexlerHttpEditorProvider.isFixedWidth() && ((count % 16)==0)){
                hexString.append("\n");
            }
            count++;
        }
        return hexString.toString();
    }
}

