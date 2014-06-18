package edu.stanford.nlp.trees.international.spanish;

import java.io.*;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import edu.stanford.nlp.io.ReaderInputStream;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasCategory;
import edu.stanford.nlp.ling.HasContext;
import edu.stanford.nlp.ling.HasIndex;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreeNormalizer;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.XMLUtils;

/**
 * A reader for XML format Spanish Treebank files.
 *
 * @author Jon Gauthier
 * @author Spence Green (original French XML reader)
 *
 */
public class SpanishXMLTreeReader implements TreeReader {

  private InputStream stream;
  private final TreeNormalizer treeNormalizer;
  private final TreeFactory treeFactory;

  private static final String NODE_SENT = "sentence";

  private static final String ATTR_WORD = "wd";
  private static final String ATTR_LEMMA = "lem";
  private static final String ATTR_FUNC = "func";
  private static final String ATTR_NAMED_ENTITY = "ne";
  private static final String ATTR_POS = "pos";

  // public static final String EMPTY_LEAF = "-NONE-";
  // public static final String MISSING_PHRASAL = "DUMMYP";
  // public static final String MISSING_POS = "DUMMY";

  private static final String EMPTY_LEAF = "-NONE-";
  private static final String MISSING_PHRASAL = "DUMMYP";
  private static final String MISSING_POS = "DUMMY";

  private NodeList sentences;
  private int sentIdx;

  /**
   * Read parse trees from a Reader.
   *
   * @param in The <code>Reader</code>
   */
  public SpanishXMLTreeReader(Reader in, boolean ccTagset) {
    // TODO custom tree normalization a la French reader?
    this(in, new LabeledScoredTreeFactory(), new TreeNormalizer());
  }

  /**
   * Read parse trees from a Reader.
   *
   * @param in Reader
   * @param tf TreeFactory -- factory to create some kind of Tree
   * @param tn the method of normalizing trees
   */
  public SpanishXMLTreeReader(Reader in, TreeFactory tf, TreeNormalizer tn) {
    TreebankLanguagePack tlp = new SpanishTreebankLanguagePack();

    stream = new ReaderInputStream(in,tlp.getEncoding());
    treeFactory = tf;
    treeNormalizer = tn;

    DocumentBuilder parser = XMLUtils.getXmlParser();
    try {
      final Document xml = parser.parse(stream);
      final Element root = xml.getDocumentElement();
      sentences = root.getElementsByTagName(NODE_SENT);
      sentIdx = 0;

    } catch (SAXException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void close() {
    try {
      if(stream != null) {
        stream.close();
        stream = null;
      }
    } catch (IOException e) {
      //Silently ignore
    }
  }

  public Tree readTree() {
    Tree t = null;
    while(t == null && sentences != null && sentIdx < sentences.getLength()) {
      int thisSentenceId = sentIdx++;
      Node sentRoot = sentences.item(thisSentenceId);
      t = getTreeFromXML(sentRoot);

      if(t != null) {
        t = treeNormalizer.normalizeWholeTree(t, treeFactory);

        // TODO calculate sentence IDs -- why can't we just use sentIdx?
        if(t.label() instanceof CoreLabel) {
        //   String ftbId = ((Element) sentRoot).getAttribute(ATTR_NUMBER);
        //   ((CoreLabel) t.label()).set(CoreAnnotations.SentenceIDAnnotation.class, ftbId);
          ((CoreLabel) t.label()).set(CoreAnnotations.SentenceIDAnnotation.class,
                                      Integer.toString(thisSentenceId));
        }
      }
    }
    return t;
  }

  private boolean isWordNode(Element node) {
    return node.hasAttribute(ATTR_WORD);
  }

  //wsg2010: Sometimes the cat attribute is not present, in which case the POS
  //is in the attribute catint, which indicates a part of a compound / MWE
  private String getPOS(Element node) {
    return node.getAttribute(ATTR_POS).trim();
  }

  /**
   * Extract the lemma attribute.
   *
   * @param node
   */
  private List<String> getLemma(Element node) {
    // String lemma = node.getAttribute(ATTR_LEMMA);
    // if (lemma == null || lemma.equals(""))
    //   return null;
    // return getWordString(lemma);

    // TODO getWordString necessary?
    return getWordString(node.getAttribute(ATTR_LEMMA));
  }

  /**
   * Extract the morphological analysis from a leaf. Note that the "ee" field
   * contains the relativizer flag.
   *
   * @param node
   */
  // private String getMorph(Element node) {
  //   String ee = node.getAttribute(ATTR_EE);
  //   return ee == null ? "" : ee;
  // }

  /**
   * Get the POS subcategory.
   *
   * @param node
   * @return
   */
  // private String getSubcat(Element node) {
  //   String subcat = node.getAttribute(ATTR_SUBCAT);
  //   return subcat == null ? "" : subcat;
  // }

  /**
   * Terminals may consist of one or more underscore-delimited tokens.
   * <p>
   * wsg2010: Marie recommends replacing empty terminals with -NONE- instead of using the lemma
   * (these are usually the determiner)
   *
   * @param text
   */
  private List<String> getWordString(String text) {
    List<String> toks = new ArrayList<String>();
    if(text == null || text.equals("")) {
      toks.add(EMPTY_LEAF);
    } else {
      //Strip spurious parens
      if(text.length() > 1)
        text = text.replaceAll("[\\(\\)]", "");

      //Check for numbers and punctuation
      String noWhitespaceStr = text.replaceAll("\\s+", "");
      if(noWhitespaceStr.matches("\\d+") || noWhitespaceStr.matches("\\p{Punct}+"))
        toks.add(noWhitespaceStr);
      else
        toks = Arrays.asList(text.split("_+"));
    }

    if(toks.size() == 0)
      throw new RuntimeException(this.getClass().getName() + ": Zero length token list for: " + text);

    return toks;
  }

  private Tree getTreeFromXML(Node root) {
    final Element eRoot = (Element) root;

    if (isWordNode(eRoot)) {
      // TODO make sure there are no children as well?

      String posStr = getPOS(eRoot);
      posStr = treeNormalizer.normalizeNonterminal(posStr);

      List<String> lemmas = getLemma(eRoot);
      // TODO
      // String morph = getMorph(eRoot);
      List<String> leafToks = getWordString(eRoot.getAttribute(ATTR_WORD).trim());
      // TODO
      // String subcat = getSubcat(eRoot);

      // TODO multiword tokens

      // if (lemmas != null && lemmas.size() != leafToks.size()) {
      //   // If this happens (and it does for a few poorly editted trees)
      //   // we assume something has gone wrong and ignore the lemmas.
      //   System.err.println("Lemmas don't match tokens, ignoring lemmas: " +
      //                      "lemmas " + lemmas + ", tokens " + leafToks);
      //   lemmas = null;
      // }

      // //Terminals can have multiple tokens (MWEs). Make these into a
      // //flat structure for now.
      Tree t = null;
      List<Tree> kids = new ArrayList<Tree>();
      // if(leafToks.size() > 1) {
      //   for (int i = 0; i < leafToks.size(); ++i) {
      //     String tok = leafToks.get(i);
      //     String s = treeNormalizer.normalizeTerminal(tok);
      //     List<Tree> leafList = new ArrayList<Tree>();
      //     Tree leafNode = treeFactory.newLeaf(s);
      //     if(leafNode.label() instanceof HasWord)
      //       ((HasWord) leafNode.label()).setWord(s);
      //     if (leafNode.label() instanceof CoreLabel && lemmas != null) {
      //       ((CoreLabel) leafNode.label()).setLemma(lemmas.get(i));
      //     }
      //     if(leafNode.label() instanceof HasContext) {
      //       ((HasContext) leafNode.label()).setOriginalText(morph);
      //     }
      //     if (leafNode.label() instanceof HasCategory) {
      //       ((HasCategory) leafNode.label()).setCategory(subcat);
      //     }
      //     leafList.add(leafNode);

      //     Tree posNode = treeFactory.newTreeNode(MISSING_POS, leafList);
      //     if(posNode.label() instanceof HasTag)
      //       ((HasTag) posNode.label()).setTag(MISSING_POS);

      //     kids.add(posNode);
      //   }
      //   t = treeFactory.newTreeNode(MISSING_PHRASAL, kids);

      // } else {
        String leafStr = treeNormalizer.normalizeTerminal(leafToks.get(0));
        Tree leafNode = treeFactory.newLeaf(leafStr);
        if (leafNode.label() instanceof HasWord)
          ((HasWord) leafNode.label()).setWord(leafStr);
        if (leafNode.label() instanceof CoreLabel && lemmas != null) {
          ((CoreLabel) leafNode.label()).setLemma(lemmas.get(0));
        }
        // TODO
        // if (leafNode.label() instanceof HasContext) {
        //   ((HasContext) leafNode.label()).setOriginalText(morph);
        // }
        // if (leafNode.label() instanceof HasCategory) {
        //   ((HasCategory) leafNode.label()).setCategory(subcat);
        // }
        kids.add(leafNode);

        t = treeFactory.newTreeNode(posStr, kids);
        if (t.label() instanceof HasTag) ((HasTag) t.label()).setTag(posStr);
      // }

      return t;
    }

    List<Tree> kids = new ArrayList<Tree>();
    for(Node childNode = eRoot.getFirstChild(); childNode != null;
        childNode = childNode.getNextSibling()) {
      if(childNode.getNodeType() != Node.ELEMENT_NODE) continue;
      Tree t = getTreeFromXML(childNode);
      if(t == null) {
        System.err.printf("%s: Discarding empty tree (root: %s)%n", this.getClass().getName(),childNode.getNodeName());
      } else {
        kids.add(t);
      }
    }

    // MWEs have a label with a
    String rootLabel = eRoot.getNodeName().trim();
    // boolean isMWE = rootLabel.equals("w") && eRoot.hasAttribute(ATTR_POS);
    // if(isMWE)
    //   rootLabel = eRoot.getAttribute(ATTR_POS).trim();

    Tree t = (kids.size() == 0) ? null : treeFactory.newTreeNode(treeNormalizer.normalizeNonterminal(rootLabel), kids);

    // if(t != null && isMWE)
    //   t = postProcessMWE(t);

    return t;
  }


  // private Tree postProcessMWE(Tree t) {
  //   String tYield = Sentence.listToString(t.yield()).replaceAll("\\s+", "");
  //   if(tYield.matches("[\\d\\p{Punct}]*")) {
  //     List<Tree> kids = new ArrayList<Tree>();
  //     kids.add(treeFactory.newLeaf(tYield));
  //     t = treeFactory.newTreeNode(t.value(), kids);
  //   } else {
  //     t.setValue(MWE_PHRASAL + t.value());
  //   }
  //   return t;
  // }


  /**
   * For debugging.
   *
   * @param args
   */
  public static void main(String[] args) {
    if(args.length < 1) {
      System.err.printf("Usage: java %s tree_file(s)%n%n",
                        SpanishXMLTreeReader.class.getName());
      System.exit(-1);
    }

    List<File> fileList = new ArrayList<File>();
    for(int i = 0; i < args.length; i++)
      fileList.add(new File(args[i]));

    TreeReaderFactory trf = new SpanishXMLTreeReaderFactory(false);
    int totalTrees = 0;
    Set<String> morphAnalyses = Generics.newHashSet();
    try {
      for(File file : fileList) {
        TreeReader tr = trf.newTreeReader(new BufferedReader(new InputStreamReader(new FileInputStream(file), "ISO-8859-1")));

        Tree t;
        int numTrees;
        String canonicalFileName = file.getName().substring(0, file.getName().lastIndexOf('.'));

        for(numTrees = 0; (t = tr.readTree()) != null; numTrees++) {
          String ftbID = ((CoreLabel) t.label()).get(CoreAnnotations.SentenceIDAnnotation.class);
          System.out.printf("%s-%s\t%s%n",canonicalFileName, ftbID, t.toString());
          List<Label> leaves = t.yield();
          for(Label label : leaves) {
            if(label instanceof CoreLabel)
              morphAnalyses.add(((CoreLabel) label).originalText());
          }
        }

        tr.close();
        System.err.printf("%s: %d trees%n",file.getName(),numTrees);
        totalTrees += numTrees;
      }

//wsg2011: Print out the observed morphological analyses
//      for(String analysis : morphAnalyses)
//        System.err.println(analysis);

      System.err.printf("%nRead %d trees%n",totalTrees);

    } catch (FileNotFoundException e) {
      e.printStackTrace();

    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
