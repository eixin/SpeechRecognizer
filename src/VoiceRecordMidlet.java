/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author saha
 */
import java.io.*;
import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.io.HttpsConnection;
import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.media.*;
import javax.microedition.media.control.*;

public class VoiceRecordMidlet extends MIDlet {
      private Display display;

      public void startApp() {
            display = Display.getDisplay(this);
            display.setCurrent(new VoiceRecordForm(this));
      }

      public void pauseApp() {
      }

      public void destroyApp(boolean unconditional) {
            notifyDestroyed();
      }
}

class VoiceRecordForm extends Form implements CommandListener {
      private StringItem message;
      private StringItem errormessage;
      private final Command record, detect, stop, exit;
      private Player player;
      private MIDlet parent;

      private ByteArrayOutputStream output;
      private byte[] recordedAudioArray = null;
      private boolean isRecording;

      public VoiceRecordForm(MIDlet p) {
            super("Recording Audio");
            parent = p;
            message = new StringItem("", "Select Record to start recording.");
            this.append(message);
            errormessage = new StringItem("", "");
            this.append(errormessage);
            record = new Command("Record", Command.OK, 0);
            this.addCommand(record);
            stop = new Command("Stop", Command.OK, 0);
            this.addCommand(record);
            detect = new Command("Detect", Command.BACK, 0);
            this.addCommand(detect);
            exit = new Command("Exit", Command.EXIT, 0);
            this.addCommand(exit);
            this.setCommandListener(this);
      }

      private void sendHttpRequest(InputStream fileToSend)
      {
        HttpConnection connection;
        try {
            connection = (HttpConnection) Connector.open("http://www.google.com/speech-api/v1/recognize?xjerr=1&client=chromium&lang=ru-RU");

            connection.setRequestMethod(HttpConnection.POST);
            connection.setRequestProperty("Content-Type", "audio/amr; rate=8000");

            OutputStream os = connection.openDataOutputStream();
            byte [] buffer = new byte[1024];
            int read=0;
            while ((read = fileToSend.read(buffer)) != -1)
                os.write(buffer, 0, read);

            os.flush();

            if (connection.getResponseCode() == HttpConnection.HTTP_OK) {
                InputStream is = connection.openInputStream();
                int ch;

                InputStreamReader reader = new InputStreamReader(is, "UTF-8");
                StringBuffer strBuffer = new StringBuffer();

                while((ch = reader.read()) != -1) {
                    strBuffer.append((char) ch);
                }

                String s = strBuffer.toString();
                String toSearch = "\"utterance\":\"";
                String recognizedString = "";
                int utteranceFound = s.indexOf(toSearch);
                if (utteranceFound != -1)
                {
                    int finalDoubleQuote = s.indexOf('"', utteranceFound + toSearch.length());
                    recognizedString = s.substring(utteranceFound + toSearch.length(), finalDoubleQuote);
                    TextBox textBox = new TextBox("", recognizedString, 1000, TextField.ANY);
                    textBox.setString(recognizedString);
                    final Command ok = new Command("OK", Command.OK, 0);
                    final Command back = new Command("Back", Command.BACK, 0);
                    final Form backForm = this;
                    textBox.addCommand(ok);
                    textBox.addCommand(back);
                    textBox.setCommandListener(new CommandListener() {

                        public void commandAction(Command c, Displayable d) {
                            if (c == ok || c == back)
                                Display.getDisplay(parent).setCurrent(backForm);
                        }
                    });
                    
                    Display.getDisplay(parent).setCurrent(textBox);
                }
                else
                {
                    Alert a = new Alert("Speech", "Unable to recognize", null, AlertType.CONFIRMATION);
                    a.setTimeout(500);
                    Display.getDisplay(parent).setCurrent(a);
                }
                System.out.println(recognizedString);
                // extract a string
                try {
                    is.close();
                    connection.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                String retval = connection.getResponseMessage() + " : " + connection.getResponseCode();
                try {
                    connection.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            errormessage.setLabel("Error");
            errormessage.setText(e.toString());
        }
      }
      public void commandAction(Command comm, Displayable disp) {
            if (comm == record) {
                  Thread t = new Thread() {
                        public void run() {
                              try {
                                    player = Manager.createPlayer("capture://audio?encoding=amr");
                                    player.realize();
                                    RecordControl rc = (RecordControl) player.getControl("RecordControl");
                                    output = new ByteArrayOutputStream();
                                    rc.setRecordStream(output);
                                    rc.startRecord();
                                    player.start();
                                    message.setText("Recording...");
                                    removeCommand(record);
                                    addCommand(stop);
                                    isRecording = true;
                              } catch (Exception e) {
                                    errormessage.setLabel("Error");
                                    errormessage.setText(e.toString());
                              }
                        }
                  };
                  t.start();

            }
            else if (comm == stop)
                stopRecording();
            else if (comm == detect) {
                Thread t = new Thread() {
                    public void run() {
                        try {
                            if (isRecording)
                                stopRecording();

                            ByteArrayInputStream recordedInputStream = new ByteArrayInputStream(recordedAudioArray);

                            //InputStream is = this.getClass().getResourceAsStream("my1.amr");
                            sendHttpRequest(recordedInputStream);
                        }
                        catch (Exception e) {
                            errormessage.setLabel("Error");
                            errormessage.setText(e.toString());
                        }
                    }
                };
                t.start();
            }
            else if (comm == exit)
                parent.notifyDestroyed();

      }

    private void stopRecording() {
        try {
            RecordControl rc = (RecordControl) player.getControl("RecordControl");
            message.setText("Recording Done!");
            rc.commit();
            recordedAudioArray = output.toByteArray();
            player.close();
        } catch (IOException e) {
            errormessage.setLabel("Error");
            errormessage.setText(e.toString());
        } finally {
            removeCommand(stop);
            addCommand(record);
            isRecording = false;
        }
    }
}