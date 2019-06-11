package js.Lucy;
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.pl.PolishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.tika.langdetect.OptimaizeLangDetector;
import org.apache.tika.language.detect.LanguageResult;

public class IndexFiles {
  
	protected final IndexWriter writer;
	protected Analyzer analyzer;
	protected Directory directory;
	
  protected IndexFiles() throws Exception {
	  	String str = System.getProperty("user.home");
		str = str+"/.index";
		directory = FSDirectory.open(Paths.get(str));
	    HashMap<String, Analyzer> analyzerPerField = new HashMap<String, Analyzer>();
	    analyzerPerField.put("bodypl", new PolishAnalyzer());
		analyzerPerField.put("bodyeng", new EnglishAnalyzer());
		analyzerPerField.put("ENGtitle", new EnglishAnalyzer());
		analyzerPerField.put("POLtitle", new EnglishAnalyzer());
		this.analyzer = new PerFieldAnalyzerWrapper(new StandardAnalyzer(), analyzerPerField);
		IndexWriterConfig iwc = new IndexWriterConfig(this.analyzer);
		if (directory.listAll().length == 0) {
	        iwc.setOpenMode(OpenMode.CREATE);
	      } else {
	        iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
	      }
		this.writer = new IndexWriter(directory, iwc);
  }

  protected void add(String dir, boolean force) {
	  	boolean isOkay = true;
	  	if(!force) {
	  		Path path = Paths.get(dir);
	  		isOkay = Files.exists(path, new LinkOption[]{ LinkOption.NOFOLLOW_LINKS});
	  	}
			if(isOkay) {
				Document doc = new Document();
				Field pathField = new StringField("dir", dir, Field.Store.YES);
			    doc.add(pathField);
			    pathField = new StringField("isWatched", "yes", Field.Store.YES);
			    doc.add(pathField);
			    try {
					this.writer.addDocument(doc);
				} catch (IOException e) {
					System.err.println(e);
				}
			}
	  	
	}

  protected void indexDocs(final IndexWriter writer, Path path) throws IOException {
    if (Files.isDirectory(path)) {
      Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          if(attrs.isRegularFile()) {
        	  try {
	            indexDoc(writer, file);
	          } catch (IOException ignore) {
	            // don't index files that can't be read.
	          }
          }
          return FileVisitResult.CONTINUE;
        }
      });
    } else {
      indexDoc(writer, path);
    }
  }

  
  protected void indexDoc(IndexWriter writer, Path file) throws IOException {
    try (InputStream stream = Files.newInputStream(file)) {
    
      Document doc = new Document();
      Parser p = new Parser();
      OptimaizeLangDetector olp = new OptimaizeLangDetector();
      
	    Field pathField = new StringField("path", file.toString(), Field.Store.YES);
	    doc.add(pathField);
	    pathField = new StringField("normal", "yes", Field.Store.YES);
	    doc.add(pathField);
	    String[] str = file.toString().split("/");
	    String name = str[str.length-1].trim();
	    String content = null;
	    try {
			content = p.parse(file);
			
		} catch(Exception e) {
		}
	    if(content == null) {
	    	return;
	    }
	   	char[] tab = content.toCharArray();
		olp.addText(tab, 0, tab.length);
		olp.loadModels();
		List<LanguageResult> list = olp.detectAll();
		for(LanguageResult x: list) {
			if(x.getLanguage().equals("en")) {
				doc.add(new TextField("bodyeng", content, Store.YES));
				doc.add(new StringField("ENGtitle", name, Field.Store.YES));
			}
			if(x.getLanguage().equals("pl")) {
				doc.add(new TextField("bodypl", content, Store.YES));
				doc.add(new StringField("POLtitle", name, Field.Store.YES));
			}
		}
		writer.updateDocument(new Term("path", file.toString()), doc);
      writer.commit();
    }
  }
}