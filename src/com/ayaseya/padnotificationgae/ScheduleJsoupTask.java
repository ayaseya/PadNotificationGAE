package com.ayaseya.padnotificationgae;

import static com.google.appengine.api.taskqueue.TaskOptions.Builder.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;

@SuppressWarnings("serial")
public class ScheduleJsoupTask extends HttpServlet {

	private final Logger logger = Logger.getLogger(getClass().getName());

	private Document document;
	private Transaction txn;

	private static final String ENTITY_KIND = "Jsoup";
	private static final String ENTITY_KEY = "Document";
	//	private static final String ACCESS_KEY_FIELD = "Html";
	// スクレイピングするページのURLを指定します。
	private static final String URL = "http://pad.gungho.jp/member/index.html";
//	private static final String URL = "http://www5a.biglobe.ne.jp/~yu-ayase/pad/";
	@SuppressWarnings("unchecked")
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		logger.info("ScheduleJsoupTaskが呼び出されました");

		resp.setContentType("text/plain;charset=UTF-8");

		// 指定したページをJsoupでスクレイピングする
		// http://ja.wikipedia.org/wiki/%E3%82%A6%E3%82%A7%E3%83%96%E3%82%B9%E3%82%AF%E3%83%AC%E3%82%A4%E3%83%94%E3%83%B3%E3%82%B0
		try {
			document = Jsoup.connect(URL).get();

		} catch (HttpStatusException e) {
			resp.getWriter().println("メンテナンス中のため処理を中断します");
			resp.getWriter().println("\n" + e);
			return;
		} catch (IOException e) {
			e.printStackTrace();
		}

		// 1ページ15行分のtitleとURLをデータストアに保存するため
		// ArrayListに整形したデータを格納します。
		ArrayList<String> SUBJECT = new ArrayList<String>();
		ArrayList<String> URL = new ArrayList<String>();
		// spanタグ内のaタグ要素を取得します。
		// <span><a>hoge</a><span>
		// 実行結果→<a>hoge</a>

		Elements href = document.select(".pickup dl dt a");// 

		for (Element tmp : href) {
			String date = tmp.attr("href").toString();// 取得したHTMLからテキスト要素のみ取り出します。
			if (!date.startsWith("http")) {
				date = "http://pad.gungho.jp/member/" + date;
			}
			URL.add(date);
		}

		Elements banner_block = document.select("#banner_block li a");// 

		for (Element tmp : banner_block) {
			String date = tmp.attr("href").toString();// 取得したHTMLからテキスト要素のみ取り出します。
			if (!date.startsWith("http")) {
				date = "http://pad.gungho.jp/member/" + date;
			}

			URL.add(date);
		}

		for (int i = 0; i < URL.size(); i++) {
			Document news = null;
			try {
				news = Jsoup.connect(URL.get(i)).get();
			} catch (HttpStatusException e) {
				return;
			} catch (IOException e) {
				e.printStackTrace();
			}

			if (news != null) {
				Elements title = news.getElementsByTag("title");
				String date = "";
				for (Element tmp : title) {					
					date = date + tmp.text();// 取得したHTMLからテキスト要素のみ取り出します。
					date = date.replaceAll("｜パズル＆ドラゴンズ", "");// 余計な文字を削除し文字列を整形します。
					date = date.replaceAll("｜ パズル＆ドラゴンズ", "");// 余計な文字を削除し文字列を整形します。				
				}
				SUBJECT.add(date);
			} 
		}

		resp.getWriter().println("\n最新の内容\n");
//		resp.getWriter().println(SUBJECT.size() + " > " + URL.size());
		for (int i = 0; i < URL.size(); i++) {
			resp.getWriter().println(SUBJECT.get(i) + " > " + URL.get(i));

		}

		// データストアのインスタンスを取得します。
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		// キーを生成します。(ここではJsoupというカインドにDocumentというname属性を持ったプライマリーキーを設定します)
		Key key = KeyFactory.createKey(ENTITY_KIND, ENTITY_KEY);
		Entity entity;
		try {
			// データストアからキーに該当するエンティティを取得します。
			entity = datastore.get(key);
		} catch (EntityNotFoundException e) {
			// 初回起動時、エンティティが存在しない場合の処理です。

			txn = datastore.beginTransaction();
			try {
				entity = new Entity(key);
				entity.setProperty("Subject", SUBJECT);
				entity.setProperty("Url", URL);

				datastore.put(entity);

				txn.commit();
			} finally {
				if (txn.isActive()) {
					txn.rollback();
				}
			}
			resp.getWriter().println("\n初回起動時のため比較するデータがありません!");
			return;

		}
		// データストアに保存された前回取得した内容を取得します。
		ArrayList<String> preSUBJECT = (ArrayList<String>) entity.getProperty("Subject");
		ArrayList<String> preURL = (ArrayList<String>) entity.getProperty("Url");
		resp.getWriter().println("\n前回の内容\n");
		for (int i = 0; i < preURL.size(); i++) {
			resp.getWriter().println(preSUBJECT.get(i) + " > " + preURL.get(i));
		}
			
		// 前回のデータと比較して新しい告知が何件存在するか検索します。
		for (int i = 0; i < URL.size(); i++) {
			if (preURL.indexOf(URL.get(i)) == -1) {
				logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>" + (i + 1) + "件目: " + SUBJECT.get(i) + "\n");
			}
		}

		// 件名のArrayListを比較して前回から変更があるかないかを判断します。
		if (URL.equals(preURL)) {
			resp.getWriter().println("\n変更なし\n");

		} else {
			resp.getWriter().println("\n変更あり\n");

			// 前回のデータと比較して新しい告知が何件存在するか検索します。
			int index = 0;
			for (int i = 0; i < URL.size(); i++) {
				if (preURL.indexOf(URL.get(i)) == -1) {
					index++;
				}
			}

			if (index == 0) {
				resp.getWriter().println("\n新しい告知はなし\n");
			} else {

				resp.getWriter().println("\n新しい告知が" + index + "件あります");
				//ServletContextインタフェースのオブジェクトを取得します。
				ServletContext sc = getServletContext();
				//データをapplicationスコープで保存します。
				sc.setAttribute("SUBJECT", SUBJECT);
				sc.setAttribute("URL", URL);
				sc.setAttribute("preURL", preURL);

				Queue queue = QueueFactory.getQueue("send");
				queue.add(withUrl("/sendAll"));
			}

			// トランザクション処理を開始します。

			txn = datastore.beginTransaction();
			try {
				entity = new Entity(key);
				entity.setProperty("Subject", SUBJECT);
				entity.setProperty("Url", URL);

				datastore.put(entity);

				txn.commit();
			} finally {
				if (txn.isActive()) {
					txn.rollback();
				}
			}

		}

	}
}
