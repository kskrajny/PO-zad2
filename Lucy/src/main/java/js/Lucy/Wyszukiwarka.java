package js.Lucy;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.pl.PolishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.complexPhrase.ComplexPhraseQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Formatter;
import org.apache.lucene.search.highlight.Fragmenter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleSpanFragmenter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;


public class Wyszukiwarka {

	protected Analyzer analyzer;
	public int lang;
	public int mode;
	public int howMany;
	public int details;
	public int color;


  protected Wyszukiwarka() throws Exception {

	    HashMap<String, Analyzer> analyzerPerField = new HashMap<String, Analyzer>();
		analyzerPerField.put("bodypl", new PolishAnalyzer());
		analyzerPerField.put("bodyeng", new EnglishAnalyzer());
		analyzerPerField.put("POLtitle", new PolishAnalyzer());
		analyzerPerField.put("ENGtitle", new EnglishAnalyzer());
		this.analyzer = new PerFieldAnalyzerWrapper(new StandardAnalyzer(), analyzerPerField);
		mode = 0;
		lang = 0;
		howMany = 0;
		details = 0;
		color = 0;

  }

  public void search(String order, Terminal terminal) throws Exception {
	  String str = System.getProperty("user.home");
	  str = str+"/.index";
	  Directory directory = FSDirectory.open(Paths.get(str));
	  String lang = "bodyeng";
	  String Tlang = "ENGtitle";
	  Query query = null;
	  if(this.lang == 1) {
		  lang = "bodypl";
		  Tlang = "POLtitle";
	  }
	  String special = lang+":" + order + " OR "+Tlang+":" + order;
	  if(this.mode == 0) {
		  QueryParser parser = new QueryParser("<default field>", this.analyzer);
		  query = parser.parse(special);
	  }
	  if(this.mode == 1) {
		  query = (new ComplexPhraseQueryParser("<default field>", this.analyzer)).parse(special);
	  }
	  if(this.mode == 2) {
		  Query query1 = new FuzzyQuery(new Term(lang, order));
		  Query query2 = new FuzzyQuery(new Term(Tlang, order));
		  query = new BooleanQuery.Builder()
				  .add(query1, BooleanClause.Occur.SHOULD)
				  .add(query2, BooleanClause.Occur.SHOULD)
				  .build();
	  }
	  	IndexReader indexReader = DirectoryReader.open(directory);
	    IndexSearcher searcher = new IndexSearcher(indexReader);
	    try {
		    TopDocs topDocs = searcher.search(query, this.howMany);
		    List<Document> documents = new ArrayList<>();
		    for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
		        documents.add(searcher.doc(scoreDoc.doc));
		    }


		    QueryScorer scorer = new QueryScorer(query);
		    Formatter formatter = new AlsoMyFormatter();
		    if(this.color == 1) {
		    	formatter = new MyFormatter();
		    }
		    Highlighter highlighter = new Highlighter(formatter, scorer);
	        Fragmenter fragmenter = new SimpleSpanFragmenter(scorer);
	        highlighter.setTextFragmenter(fragmenter);
	        terminal.writer()
			.println("File count: "+topDocs.scoreDocs.length);
	        for (int i = 0; i < topDocs.scoreDocs.length; i++)
	        {
	        	boolean isOkay = true;
	            int docid = topDocs.scoreDocs[i].doc;
	            Document doc = searcher.doc(docid);
	            String title = doc.get("path");
	            terminal.writer()
				.print(title);
	            String text = doc.get(lang);
	            TokenStream tokenStream;
	            String[] frags = null;
	            try {
	            	tokenStream = analyzer.tokenStream(lang, text);
	            	frags = highlighter.getBestFragments(tokenStream, text, 100);
	            } catch (Exception e) {
	            	isOkay = false;
	            }
	            if(isOkay) {
		            if(this.details == 1) {
		            	terminal.writer()
						.println(":");
		            	for (String frag : frags)
		            	{
		            		terminal.writer()
							.println(new AttributedStringBuilder().append(frag).toAnsi());
		            	}
		            }
	            }
	            terminal.writer()
				.println("");
	        }

	    } catch (Exception e) {
	    	terminal.writer()
			.println("ERROR");
	    }

  }

  public static void main(String[] args) throws Exception {
	  Wyszukiwarka w = new Wyszukiwarka();

	  while(true) {
		  try (Terminal terminal = TerminalBuilder.builder()
				  	.jna(false)
					.jansi(true)
					.build()) {
					while (true) {
						LineReader lineReader = LineReaderBuilder.builder()
								.terminal(terminal)
								.build();
						String order = null;
						try {
							order = lineReader.readLine("> ");
							order = order.trim();
							  String[] tab = order.split(" ");
							  if(!order.equals("")) {
								  if(tab[0].startsWith("%") && tab.length == 2){

									  if(tab[0].equals("%lang")) {
										  if(tab[1].equals("en")) {
											  w.lang = 0;
										  }
										  if(tab[1].equals("pl")) {
											  w.lang = 1;
										  }
									  }
									  if(tab[0].equals("%limit")) {
										  int n = -1;
										  try {
											 n = Integer.parseInt(tab[1]);
										  } catch (Exception e) {
										  }
										  if(n >= 0) {
											  w.howMany = n;
										  }
									  }
									  if(tab[0].equals("%details")) {
										  if(tab[1].equals("on")) {
											  w.details = 1;
										  }
										  if(tab[1].equals("off")) {
											  w.details = 0;
										  }
									  }
									  if(tab[0].equals("%color")) {
										  if(tab[1].equals("on")) {
											  w.color = 1;
										  }
										  if(tab[1].equals("off")) {
											  w.color = 0;
										  }
									  }
								  }
								  if(tab[0].startsWith("%") && tab.length == 1){
									  if(tab[0].equals("%term")) {
										  w.mode = 0;
									  }
									  if(tab[0].equals("%phrase")) {
										  w.mode = 1;
									  }
									  if(tab[0].equals("%fuzzy")) {
										  w.mode = 2;
									  }
								  }
								  if(!tab[0].startsWith("%")) {
									  w.search(order, terminal);
								  }
							  }
						} catch (UserInterruptException e) {
							return;
						} catch (EndOfFileException e) {
							return;
						}
					}
				} catch (IOException e) {

				}

	  }
  }
}
