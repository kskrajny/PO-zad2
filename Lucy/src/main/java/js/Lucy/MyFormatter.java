package js.Lucy;

import org.apache.lucene.search.highlight.Formatter;
import org.apache.lucene.search.highlight.TokenGroup;

public class MyFormatter implements Formatter {
	  
	  private static final String DEFAULT_PRE_TAG = "\033[31m";
	  private static final String DEFAULT_POST_TAG = "\033[0m"; 
	  
	  private String preTag;
	  private String postTag;

	  public MyFormatter(String preTag, String postTag) {
	    this.preTag = preTag;
	    this.postTag = postTag;
	  }

	  /** Default constructor uses HTML: &lt;B&gt; tags to markup terms. */
	  public MyFormatter() {
	    this(DEFAULT_PRE_TAG, DEFAULT_POST_TAG);
	  }

	  /* (non-Javadoc)
	   * @see org.apache.lucene.search.highlight.Formatter#highlightTerm(java.lang.String, org.apache.lucene.search.highlight.TokenGroup)
	   */
	  @Override
	  public String highlightTerm(String originalText, TokenGroup tokenGroup) {
	    if (tokenGroup.getTotalScore() <= 0) {
	      return originalText;
	    }

	    // Allocate StringBuilder with the right number of characters from the
	    // beginning, to avoid char[] allocations in the middle of appends.
	    StringBuilder returnBuffer = new StringBuilder(preTag.length() + originalText.length() + postTag.length());
	    returnBuffer.append(preTag);
	    returnBuffer.append(originalText);
	    returnBuffer.append(postTag);
	    return returnBuffer.toString();
	  }
	}