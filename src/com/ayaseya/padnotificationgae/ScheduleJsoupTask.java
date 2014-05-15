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
	private static final String URL = "http://www5a.biglobe.ne.jp/~yu-ayase/pad/";

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
			resp.getWriter().println("\n"+e);
			return;
		} catch (IOException e) {
			e.printStackTrace();
		}

		// 1ページ15行分のtitleとURLをデータストアに保存するため
		// ArrayListに整形したデータを格納します。

		// spanタグ内のaタグ要素を取得します。
		// <span><a>hoge</a><span>
		// 実行結果→<a>hoge</a>
		Elements titles = document.select("span a");// 告知の件名です。
		Elements dates = document.select("tr td font strong");// 告知した日付です。
		Elements icon = document.select("tr td font img");// アイコン画像です。

		//アイコン情報を格納します。
		ArrayList<String> ICON = new ArrayList<String>();
		for (Element tmp : icon) {

			if (!tmp.attr("src").toString().endsWith("line.gif")) {
				String str = tmp.attr("src").toString();
				str = str.replaceAll("/nol/index2_image/", "");
				str = str.replaceAll(".gif", "");
				ICON.add(str);
			}
		}

		ArrayList<String> TITLE = new ArrayList<String>();

		// 日付をArrayListに格納します。
		for (Element tmp : dates) {
			String date = tmp.text();// 取得したHTMLからテキスト要素のみ取り出します。
			// <strong>タグの要素には日付以外にも<img>タグの要素(改行コードのみ)も取得していたため
			// 日付のみArrayListに格納する処理にします。
			if (!date.equals("")) {
				date = date.replaceAll("\\.", "/");// 2014.01.01→2014/01/01に置き換えます。
				TITLE.add(date + " ");// ArrayListに格納します。(見やすいように末尾に空白を連結します)
			}
		}

		// 日付に件名を追記します。
		int index = 0;
		for (Element tmp : titles) {
			String title = TITLE.get(index) + tmp.text();// 日付に件名を文字列結合します。
			TITLE.set(index, title); // indexを指定してArrayListの要素を置き換えます。
			index++;
		}

		// URL(リンク先)もArrayListに格納します。
		Elements href = document.select("tr td span a");
		ArrayList<String> LINK = new ArrayList<String>();
		for (Element tmp : href) {
			if ((tmp.attr("href").toString()).startsWith("http")) {// 前方一致検索でhttpで始まる文字列か確認する
				LINK.add(tmp.attr("href").toString()); // httpから始まる文字列(絶対パス)
			} else {
				LINK.add(URL + tmp.attr("href").toString()); // httpから始まらない文字列(相対パス)
			}

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
				entity.setProperty("Title", TITLE);
				entity.setProperty("Url", LINK);
				entity.setProperty("Icon", ICON);

				datastore.put(entity);

				txn.commit();
			} finally {
				if (txn.isActive()) {
					txn.rollback();
				}
			}
			resp.getWriter().println("初回起動時のため比較するデータがありません");
			return;

		}
		// データストアに保存された前回取得した内容を取得します。
		ArrayList<String> preTITLE = (ArrayList<String>) entity.getProperty("Title");
		resp.getWriter().println("\n前回の内容\n");
		for (int i = 0; i < preTITLE.size(); i++) {
			resp.getWriter().println(preTITLE.get(i));
		}

		// 件名のArrayListを比較して前回から変更があるかないかを判断します。
		if (TITLE.equals(preTITLE)) {
			resp.getWriter().println("\n変更なし\n");

		} else {
			resp.getWriter().println("\n変更あり\n");

			//ServletContextインタフェースのオブジェクトを取得します。
			ServletContext sc = getServletContext();
			//データをapplicationスコープで保存します。
			sc.setAttribute("TITLE", TITLE);
			sc.setAttribute("preTITLE", preTITLE);
			sc.setAttribute("LINK", LINK);
			sc.setAttribute("ICON", ICON);

			Queue queue = QueueFactory.getQueue("send");
			queue.add(withUrl("/sendAll"));

			// トランザクション処理を開始します。

			txn = datastore.beginTransaction();
			try {
				entity = new Entity(key);
				entity.setProperty("Title", TITLE);
				entity.setProperty("Url", LINK);
				entity.setProperty("Icon", ICON);

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
