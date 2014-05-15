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

import static com.ayaseya.padnotificationgae.CommonUtilities.*;

import java.util.logging.Logger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;

/**
 * Context initializer that loads the API key from the App Engine datastore.
 */
// ServletContextListener
// Servletコンテキストの変更に関する通知を受け取る処理を実装していきます。
public class ApiKeyInitializer implements ServletContextListener {

	static final String ATTRIBUTE_ACCESS_KEY = "apiKey";

	private static final String ENTITY_KIND = "Settings";
	private static final String ENTITY_KEY = "MyKey";
	private static final String ACCESS_KEY_FIELD = "ApiKey";

	private final Logger logger = Logger.getLogger(getClass().getName());

	// Webアプリケーションが初期化処理を開始したことを通知します。
	public void contextInitialized(ServletContextEvent event) {
		logger.info("ApiKeyInitializerが呼び出されました");
		
		// データストアのインスタンスを取得します。
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		// キーを生成します。(ここではSettingsというカインドにMyKeyというname属性を持ったプライマリーキーを設定します)
		Key key = KeyFactory.createKey(ENTITY_KIND, ENTITY_KEY);

		Entity entity;
		try {
			// データストアからキーに該当するエンティティを取得します。
			entity = datastore.get(key);
		} catch (EntityNotFoundException e) {
			// 初回起動時、エンティティが存在しない場合は
			// エンティティ
			entity = new Entity(key);
			// NOTE: it's not possible to change entities in the local server,
			// so
			// it will be necessary to hardcode the API key below if you are
			// running
			// it locally.
			// entity.setProperty(ACCESS_KEY_FIELD,
			// "replace_this_text_by_your_Simple_API_Access_key");
			
			// (ここではApiKeyというプロパティ(カラム)にGAEのサーバーAPIを値として保存します)
			entity.setProperty(ACCESS_KEY_FIELD, SERVER_API_KEY);
			// データベースにエンティティを保存します。
			datastore.put(entity);
			
			logger.severe("Created fake key. Please go to App Engine admin "
					+ "console, change its value to your API Key (the entity "
					+ "type is '" + ENTITY_KIND
					+ "' and its field to be changed is '" + ACCESS_KEY_FIELD
					+ "'), then restart the server!");
		}
		// データストアに保存されたサーバーAPIを取得します。
		String accessKey = (String) entity.getProperty(ACCESS_KEY_FIELD);
		// ServletコンテキストにAPIの情報を付加します。
		event.getServletContext().setAttribute(ATTRIBUTE_ACCESS_KEY, accessKey);
	}

	// Servlet コンテキストがシャットダウン処理に入ることを通知します。
	public void contextDestroyed(ServletContextEvent event) {
		logger.info("ServletContextがシャットダウン処理に入りました");
	}

}
