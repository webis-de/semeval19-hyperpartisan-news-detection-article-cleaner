package de.webis.semeval19;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.jsoup.Jsoup;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Whitelist;
import org.jsoup.select.Elements;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class ArticleCleaner {
  
  private static final String[] ALLOWED_TAGS = { "a", "p", "q", "blockquote" };
  
  private static final String[] ALLOWED_A_ATTRIBUTES = { "href", "type" };
  
  private static final Whitelist TAG_WHITELIST = new Whitelist()
      .addTags(ALLOWED_TAGS).addAttributes("a", ALLOWED_A_ATTRIBUTES);
  
  //////////////////////////////////////////////////////////////////////////////
  // MAIN
  //////////////////////////////////////////////////////////////////////////////
  
  public static void main(final String[] args)
  throws Exception {
    final String groundTruthFileName = args[0];
    final String articlesFileName = args[1];

    final Map<String, String> articleHosts =
        ArticleCleaner.readArticleHosts(groundTruthFileName);

    final Document articles = ArticleCleaner.readXml(articlesFileName);
    final NodeList articleList = articles.getElementsByTagName("article");
    for (int a = 0; a < articleList.getLength(); ++a) {
      final Element article = (Element) articleList.item(a);
      final String id = article.getAttribute("id");
      final String hots = articleHosts.get(id);
      if (hots == null) {
        throw new NullPointerException("Unknown ID: " + id);
      }
      ArticleCleaner.cleanArticle(articles, article, hots);
    }
    
    ArticleCleaner.writeXml(articles);
  }
  
  //////////////////////////////////////////////////////////////////////////////
  // CLEAN ARTICLE
  //////////////////////////////////////////////////////////////////////////////
  
  private static void cleanArticle(
      final Document document, final Element article, final String host) {
    final String articleHtml = article.getTextContent();
    final org.jsoup.nodes.Document jsoupArticle = Jsoup.parse(articleHtml);
    ArticleCleaner.cleanArticle(jsoupArticle, host);
    
    article.setTextContent("");
    for (final org.jsoup.nodes.Node node : jsoupArticle.childNodes()) {
      article.appendChild(ArticleCleaner.cleanNode(document, node, host));
    }
  }
  
  private static Node cleanNode(
      final Document document, final org.jsoup.nodes.Node jsoupNode,
      final String host) {
    if (jsoupNode instanceof TextNode) {
      return document.createTextNode(jsoupNode.toString());
    } else if (jsoupNode instanceof org.jsoup.nodes.Element) {
      final org.jsoup.nodes.Element jsoupElement =
          (org.jsoup.nodes.Element) jsoupNode;
      final Element element = document.createElement(
          ArticleCleaner.getTagName(jsoupElement));
      if (element.getTagName().equals("a")) {
        ArticleCleaner.cleanLink(element, jsoupElement, host);
      }
      
      // recursive
      for (final org.jsoup.nodes.Node child : jsoupElement.childNodes()) {
        element.appendChild(ArticleCleaner.cleanNode(document, child, host));
      }
      return element;
    } else {
      throw new IllegalArgumentException(jsoupNode.toString());
    }
  }
  
  private static String getTagName(final org.jsoup.nodes.Element element) {
    final String tagName = element.tagName();
    if (tagName.equals("blockquote")) {
      return "q";
    } else {
      return tagName;
    }
  }
  
  private static void cleanLink(
      final Element element, final org.jsoup.nodes.Element jsoupElement,
      final String host) {
    final String href = jsoupElement.attr("href");
    final String hrefHost =
        href.replaceAll("https?://", "").replaceAll("/.*", "");
    if (hrefHost.endsWith(host)) {
      element.setAttribute("type", "internal");
    } else {
      element.setAttribute("href",
          href.replaceAll("%%", "%").replaceAll("%$", ""));
      element.setAttribute("type", "external");
    }
  }
  
  private static void cleanArticle(
      final org.jsoup.nodes.Document article, final String url) {
   
    for (final String allowedTag : ALLOWED_TAGS) {
      final Elements elements = article.select(allowedTag);
      for (final org.jsoup.nodes.Element element : elements) {
        final String text = element.html();
        final String cleanedText = Jsoup.clean(text, TAG_WHITELIST);
        element.html(cleanedText);
      }
    }
    final String cleaned = Jsoup.clean(article.toString(), TAG_WHITELIST);
    article.html(cleaned);
  }
  
  //////////////////////////////////////////////////////////////////////////////
  // READ GROUND TRUTH
  //////////////////////////////////////////////////////////////////////////////
  
  private static Map<String, String> readArticleHosts(
      final String groundTruthFileName)
  throws SAXException, IOException {
    final Document articles = ArticleCleaner.readXml(groundTruthFileName);
    final Map<String, String> articleUrls = new HashMap<>();
    final NodeList articleList = articles.getElementsByTagName("article");
    for (int a = 0; a < articleList.getLength(); ++a) {
      final Element article = (Element) articleList.item(a);
      final String id = article.getAttribute("id");
      final String url = article.getAttribute("url");
      articleUrls.put(id, ArticleCleaner.getHost(url));
    }
    return articleUrls;
  }
  
  private static String getHost(final String url)
  throws MalformedURLException {
    return new URL(url).getHost();
  }
  
  //////////////////////////////////////////////////////////////////////////////
  // UTILITY
  //////////////////////////////////////////////////////////////////////////////
  
  private static Document readXml(final String fileName)
  throws SAXException, IOException {
    final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    try {
      final DocumentBuilder builder = factory.newDocumentBuilder();
      return builder.parse(fileName);
    } catch (final ParserConfigurationException e) {
      // should not happen... we use default configuration!
      throw new RuntimeException(e);
    }
  }
  
  private static void writeXml(final Document xml)
  throws TransformerException {
    final TransformerFactory factory = TransformerFactory.newInstance();
    try {
      final Transformer transformer = factory.newTransformer();
      
      final DOMSource source = new DOMSource(xml.getDocumentElement());
      final StreamResult output = new StreamResult(System.out);
      transformer.transform(source, output);
    } catch (final TransformerConfigurationException e) {
      // should not happen... we use default configuration!
      throw new RuntimeException(e);
    }
  }

}
