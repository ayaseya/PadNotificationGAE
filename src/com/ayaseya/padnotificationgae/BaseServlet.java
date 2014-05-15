/*
 * Copyright 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ayaseya.padnotificationgae;

import java.io.IOException;
import java.util.Enumeration;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Skeleton class for all servlets in this package.
 */
@SuppressWarnings("serial")
abstract class BaseServlet extends HttpServlet {

	// change to true to allow GET calls
	static final boolean DEBUG = true;

	protected final Logger logger = Logger.getLogger(getClass().getName());

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException, ServletException {
		if (DEBUG) {
			doPost(req, resp);
		} else {
			super.doGet(req, resp);
		}
	}

	// HttpServletRequestから引数で渡したパラメーターの要素を取得します。
	protected String getParameter(HttpServletRequest req, String parameter)
			throws ServletException {
		String value = req.getParameter(parameter);
		if (isEmptyOrNull(value)) {
			if (DEBUG) {
				StringBuilder parameters = new StringBuilder();
				
		        // Enumerationはコレクション・フレームワークの導入以前のインターフェースです。
				// 後継となるIteratorインタフェースはコレクション内の要素に順番にアクセスする手段を提供します。
				// http://www.javadrive.jp/servlet/request/index5.html
				// getParameterNamesで全てのパラメーター名を取得します。
				@SuppressWarnings("unchecked")
				Enumeration<String> names = req.getParameterNames();
				while (names.hasMoreElements()) {// names要素がある限りループ処理を実行します。
					String name = names.nextElement();// nextElement()で次の要素を取得します。
					// リクエストパラメータの「名前」を引数に指定すると「値」を取得することが出来ます。
					String param = req.getParameter(name);
					// 文字列結合
					parameters.append(name).append("=").append(param)
							.append("\n");
				}
				logger.fine("parameters: " + parameters);
			}
			throw new ServletException("Parameter " + parameter + " not found");
		}
		return value.trim();
	}

	protected String getParameter(HttpServletRequest req, String parameter,
			String defaultValue) {
		String value = req.getParameter(parameter);
		if (isEmptyOrNull(value)) {
			value = defaultValue;
		}
		return value.trim();
	}

	protected void setSuccess(HttpServletResponse resp) {
		setSuccess(resp, 0);
	}

	protected void setSuccess(HttpServletResponse resp, int size) {

		// http://www.javadrive.jp/servlet/response/index4.html
		// クライアントにレスポンスを返す際に、ステータスコードを設定しなかった場合には
		// デフォルトで「SC_OK」が設定されます。

		resp.setStatus(HttpServletResponse.SC_OK);

		// http://www.javadrive.jp/servlet/response/index2.html
		// クライアントに対して何か出力するにあたってまず行うべき事は
		// どのようなデータを送るのかを指定するコンテンツタイプの設定です。
		// HTTPレスポンスヘッダの中の「Content-Type」を設定します。
		resp.setContentType("text/plain");

		// Content-Length ヘッダを設定して、パフォーマンス向上のために、
		// サーブレットコンテナが持続的な接続を使用し、
		// 応答をクライアントに返すことができるようにします。
		// 応答全体が応答バッファ内に収まる場合は、コンテンツの長さが自動的に設定されます。
		resp.setContentLength(size);
	}

	protected boolean isEmptyOrNull(String value) {
		// value == nullで文字列がnullが否か判断する、nullならtrueを返す
		// OR判定はいずれかが成立（true）になれば、以降の条件を見に行かない
		// そのためvalue.trim().length()でNullPointerExceptionは発生しない
		// 文字列に複数の空白が送られてくる可能性があるので
		// value.trim().length()で文字列の長さが0文字だった場合でもtrueを返します。
		// trim()…文字列に先頭又は最後に空白文字がくっ付いている場合、それらを全て取り除きます。
		// AndroidのTextUtils.isEmptyメソッドと同様の処理
		// http://developer.android.com/reference/android/text/TextUtils.html#isEmpty(java.lang.CharSequence)
		return value == null || value.trim().length() == 0;
	}

}
