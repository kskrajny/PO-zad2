package js.Lucy;

import java.io.IOException;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import java.nio.file.Path;
import org.xml.sax.SAXException;

public class Parser {
	
   public String parse(Path file) throws IOException, TikaException, SAXException {
      Tika tika = new Tika();
      String filetype = tika.detect(file);
      if(!filetype.equals("text/plain") &&
    		  !filetype.equals("application/pdf") &&
    		  !filetype.equals("application/rtf") &&
    		  !filetype.equals("application/vnd.oasis.opendocument.text") &&
    		  !filetype.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
    	  return null;
      }
      String content = null;
      try {
    	  content = tika.parseToString(file);
      } catch(Exception e) {
    	return null;  
      }
      return content;
   }		 
}