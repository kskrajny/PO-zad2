package js.Lucy;

import java.nio.file.*;
import static java.nio.file.StandardWatchEventKinds.*;
import java.nio.file.attribute.*;
import java.io.*;
import java.util.*;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

public class Indekser {
	
	private final WatchService watcher;
	private final Map<WatchKey, Path> keys;

	@SuppressWarnings("unchecked")
	static <T> WatchEvent<T> cast(WatchEvent<?> event) {
		return (WatchEvent<T>) event;
	}
	
	private void register(Path dir) throws IOException {
		WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
		keys.put(key, dir);
	}

	private void registerAll(final Path start) throws IOException {
		
		Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				register(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	Indekser(List<Path> paths) throws Exception {
		this.watcher = FileSystems.getDefault()
			.newWatchService();
		this.keys = new HashMap<WatchKey, Path>();
		for(Path x: paths) {
			registerAll(x);
		}
	}

	
	void processEvents(IndexFiles ind) throws Exception {
		
		for (;;) {
			WatchKey key;
			try {
				key = watcher.take();
			} catch (InterruptedException x) {
				return;
			}

			Path dir = keys.get(key);
			if (dir == null) {
	
				continue;
			}

			for (WatchEvent<?> event : key.pollEvents()) {
				WatchEvent.Kind<?> kind = event.kind();

				if (kind == OVERFLOW) {
					continue;
				}

				
				WatchEvent<Path> ev = cast(event);
				Path name = ev.context();
				Path child = dir.resolve(name);
			
				
				if(kind == ENTRY_DELETE) {
					ind.writer.deleteDocuments(new Term("path", child.toString()));
					QueryParser parser = new QueryParser("normal", ind.analyzer);
					Query query = parser.parse("yes");
					IndexReader indexReader = DirectoryReader.open(ind.directory);
				    IndexSearcher searcher = new IndexSearcher(indexReader);
					TopDocs topDocs = searcher.search(query, Integer.MAX_VALUE);
					List<Document> documents = new ArrayList<>();
					for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
					    documents.add(searcher.doc(scoreDoc.doc));
					}
					parser = new QueryParser("path", ind.analyzer);
					for(Document x: documents) {
				    	if(x.getField("path").stringValue().startsWith(child.toString())) {
				    		ind.writer.deleteDocuments(new Term("path", x.getField("path").stringValue()));
				    	}
				    }
				    ind.writer.commit();
					 	
				}
				// register it and its sub-directories
				if ((kind == ENTRY_CREATE)) {
					System.out.println("Create: "+child.toString());
					try {
						ind.indexDocs(ind.writer, child);
						registerAll(child);
					} catch (IOException e) {
						System.err.println(e);
					}
				}
			}

			// reset key and remove from set if directory no longer accessible
			boolean valid = key.reset();
			if (!valid) {
				keys.remove(key);

				// all directories are inaccessible
				if (keys.isEmpty()) {
					break;
				}
			}
		}
	}

	
	public static void main(String[] args) {
		IndexReader indexReader = null;
		IndexFiles help = null;
		try {
			final IndexFiles ind = new IndexFiles();
			help = ind;
			if (args.length == 0) {
				QueryParser parser = new QueryParser("isWatched", ind.analyzer);
				Query query = parser.parse("yes");
				try {
					indexReader = DirectoryReader.open(ind.directory);
					IndexSearcher searcher = new IndexSearcher(indexReader);
				    TopDocs topDocs = searcher.search(query, Integer.MAX_VALUE);
				    List<Document> documents = new ArrayList<>();
				    for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
				        documents.add(searcher.doc(scoreDoc.doc));
				    }
				    List<Path> paths = new ArrayList<>();
				    for(Document x: documents) {
				    	Path path = Paths.get(x.getField("dir").stringValue());
				    	ind.indexDocs(ind.writer, path);
				    	paths.add(path);
				    }
				    
					Runtime.getRuntime()
					.addShutdownHook(new Thread() {
						@Override
						public void run() {
							try {
								ind.writer.close();
							} catch (IOException e) {
								System.err.println("asfsdf\n"+e);
							}
						}
					});
					new Indekser(paths).processEvents(ind);
				} catch (Exception e) {
					System.err.println(e);
					return;
				}
			}
			
			if(args.length == 2 && args[0].equals("--add")) {
				 ind.add(args[1], false);
				 ind.writer.commit();
				 ind.writer.close();
				
			}
			
			if(args.length == 2 && args[0].equals("--rm")) {
				ind.writer.deleteDocuments(new Term("dir", args[1]));
				QueryParser parser = new QueryParser("normal", ind.analyzer);
				Query query = parser.parse("yes");
				indexReader = DirectoryReader.open(ind.directory);
			    IndexSearcher searcher = new IndexSearcher(indexReader);
				TopDocs topDocs = searcher.search(query, Integer.MAX_VALUE);
				List<Document> documents = new ArrayList<>();
				for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
				    documents.add(searcher.doc(scoreDoc.doc));
				}
				parser = new QueryParser("path", ind.analyzer);
				for(Document x: documents) {
			    	if(x.getField("path").stringValue().startsWith(args[1])) {
			    		ind.writer.deleteDocuments(new Term("path", x.getField("path").stringValue()));
			    	}
			    }
				ind.writer.commit();
				ind.writer.close();
				indexReader.close();
				ind.directory.close();
			}
			
			if (args.length == 1) {
				if(args[0].equals("--purge")) {
					ind.writer.deleteAll();
					ind.writer.commit();
					ind.writer.close();
					ind.directory.close();
				}
				if(args[0].equals("--reindex")) {
					QueryParser parser = new QueryParser("isWatched", ind.analyzer);
					Query query = parser.parse("yes");
					indexReader = DirectoryReader.open(ind.directory);
				    IndexSearcher searcher = new IndexSearcher(indexReader);
					TopDocs topDocs = searcher.search(query, Integer.MAX_VALUE);
					List<Document> documents = new ArrayList<>();
					for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
					    documents.add(searcher.doc(scoreDoc.doc));
					}
					ind.writer.deleteAll();
					indexReader.close();
					    
					    for(Document x: documents) {
					    	Path path = Paths.get(x.getField("dir").stringValue());
					    	boolean isOkay = Files.exists(path, new LinkOption[]{ LinkOption.NOFOLLOW_LINKS});
					    	ind.add(x.getField("dir").stringValue(), true);
					    	if(isOkay) {
						    	ind.indexDocs(ind.writer, path);
					    	}
					    }
					    ind.writer.commit();
						ind.writer.close();
				}
				
				if(args[0].equals("--list")) {
					QueryParser parser = new QueryParser("isWatched", ind.analyzer);
					Query query = parser.parse("yes");
					
						indexReader = DirectoryReader.open(ind.directory);
					    IndexSearcher searcher = new IndexSearcher(indexReader);
						    TopDocs topDocs = searcher.search(query, Integer.MAX_VALUE);
						    List<Document> documents = new ArrayList<>();
						    for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
						        documents.add(searcher.doc(scoreDoc.doc));
						    }
						indexReader.close();
						for(Document x: documents) {
					    	System.out.println(x.getField("dir").stringValue());	
						}
					
			    }
			}
		} catch (Exception e) {
			try {
				indexReader.close();
				help.writer.close();
			} catch (Exception i) {
			}
			System.err.println(e);
			return;
		}	
	}
}
