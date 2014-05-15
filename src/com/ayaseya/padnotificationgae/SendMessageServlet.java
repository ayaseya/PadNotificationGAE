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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.android.gcm.server.Constants;
import com.google.android.gcm.server.Message;
import com.google.android.gcm.server.MulticastResult;
import com.google.android.gcm.server.Result;
import com.google.android.gcm.server.Sender;

/**
 * Servlet that sends a message to a device.
 * <p>
 * This servlet is invoked by AppEngine's Push Queue mechanism.
 */
// このサーブレットはGAEのタスクキュー（queue）の仕組みにより呼びだされます。
// GAEではリクエストの処理時間に制限があるため、メールの大量送信などの処理などに対応する場合には
// タスクキューを使用して送信処理を分割します。
@SuppressWarnings("serial")
public class SendMessageServlet extends BaseServlet {

	private static final String HEADER_QUEUE_COUNT = "X-AppEngine-TaskRetryCount";
	private static final String HEADER_QUEUE_NAME = "X-AppEngine-QueueName";
	private static final int MAX_RETRY = 3;

	static final String PARAMETER_DEVICE = "device";
	static final String PARAMETER_MULTICAST = "multicastKey";
	ArrayList<String> TITLE;
	ArrayList<String> preTITLE;
	ArrayList<String> LINK;
	ArrayList<String> ICON;
	
	ArrayList<Integer> updateIndex;

	private Sender sender;

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		sender = newSender(config);
	}

	/**
	 * Creates the {@link Sender} based on the servlet settings.
	 */
	protected Sender newSender(ServletConfig config) {
		String key = (String) config.getServletContext().getAttribute(
				ApiKeyInitializer.ATTRIBUTE_ACCESS_KEY);
		return new Sender(key);
	}

	/**
	 * Indicates to App Engine that this task should be retried.
	 */
	private void retryTask(HttpServletResponse resp) {
		resp.setStatus(500);// 500=SC_INTERNAL_SERVER_ERROR…サーバー側の問題によりエラーが発生した場合のステータスです。
	}

	/**
	 * Indicates to App Engine that this task is done.
	 */
	private void taskDone(HttpServletResponse resp) {
		resp.setStatus(200);// 200=SC_OK…正常にデータが送信できた場合のステータスです。
	}

	/**
	 * Processes the request to add a new message.
	 */
	@SuppressWarnings("unchecked")
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		if (req.getHeader(HEADER_QUEUE_NAME) == null) {
			throw new IOException("Missing header " + HEADER_QUEUE_NAME);
		}
		// ヘッダー名に対応するHTTPヘッダ情報を返します。
		// HEADER_QUEUE_COUNT="X-AppEngine-TaskRetryCount"
		String retryCountHeader = req.getHeader(HEADER_QUEUE_COUNT);
		logger.fine("retry count: " + retryCountHeader);
		if (retryCountHeader != null) {
			int retryCount = Integer.parseInt(retryCountHeader);
			if (retryCount > MAX_RETRY) {// リトライ回数が設定していた回数を超えると処理を中止します。
				logger.severe("Too many retries, dropping task");
				taskDone(resp);
				return;
			}
		}
		//
		String regId = req.getParameter(PARAMETER_DEVICE);

		//ServletContextインタフェースのオブジェクトを取得
		ServletContext sc = getServletContext();
		//データをapplicationスコープで保存
		sc.getAttribute("LIST");

		TITLE = (ArrayList<String>) sc.getAttribute("TITLE");
		preTITLE = (ArrayList<String>) sc.getAttribute("preTITLE");
		LINK = (ArrayList<String>) sc.getAttribute("LINK");
		ICON = (ArrayList<String>) sc.getAttribute("ICON");

		updateIndex = new ArrayList<Integer>();

		// 前回のデータと比較して新しい告知が何件存在するか検索します。
		for (int i = 0; i < TITLE.size(); i++) {
			if (preTITLE.indexOf(TITLE.get(i)) == -1) {
				updateIndex.add(i);
				logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>" + (i+1) + "件目: " + TITLE.get(i) + "\n");

			}
		}

		if (regId != null) {
			sendSingleMessage(regId, resp);// 1端末だった場合、メッセージを送信します。
			return;
		}
		//
		String multicastKey = req.getParameter(PARAMETER_MULTICAST);
		if (multicastKey != null) {
			sendMulticastMessage(multicastKey, resp);// 複数端末だった場合、メッセージを送信します。
			return;
		}
		logger.severe("Invalid request!");//
		taskDone(resp);
		return;
	}

	// 1端末にメッセージを送信する場合の処理
	private void sendSingleMessage(String regId, HttpServletResponse resp) {
		logger.info("Sending message to device " + regId);
		//		Message message = new Message.Builder().build();

		Message.Builder builder = new Message.Builder();
		builder.addData("INDEX", String.valueOf(updateIndex.size())); // 更新された件数です。
		for (int i = 0; i < updateIndex.size(); i++) {

			builder.addData("TITLE"+(i+1), TITLE.get(updateIndex.get(i))); // 送信する件名データです。
			builder.addData("URL"+(i+1), LINK.get(updateIndex.get(i))); // 送信するURLデータです。
			builder.addData("ICON"+(i+1), ICON.get(updateIndex.get(i))); // 送信するICONデータです。

		}
		Message message = builder.build();

		Result result;
		try {
			result = sender.sendNoRetry(message, regId);
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Exception posting " + message, e);
			taskDone(resp);
			return;
		}
		if (result == null) {
			retryTask(resp);
			return;
		}
		if (result.getMessageId() != null) {// メッセージが正常に作成されると、getMessageId（）は、メッセージIDを返します。
			logger.info("Succesfully sent message to device " + regId);
			String canonicalRegId = result.getCanonicalRegistrationId();
			if (canonicalRegId != null) {
				// same device has more than on registration id: update it
				// http://kinsentansa.blogspot.jp/2013/04/gcmcanonical-registration-id.html
				// 「アプリケーションのアップデート」や「バックアップとリストア」などにより、
				// GCMサーバーに同じデバイスで複数のRegistration ID（以下regId）が割り振られる場合があり、
				// その場合にcanonicalRegIdが取得できる状況となります。
				// その場合には、データストアを更新します。
				logger.finest("canonicalRegId " + canonicalRegId);
				Datastore.updateRegistration(regId, canonicalRegId);
			}
		} else {// メッセージが正常に作成されないと、getMessageId()は、Nullを返します。
			String error = result.getErrorCodeName();
			if (error.equals(Constants.ERROR_NOT_REGISTERED)) {
				// application has been removed from device - unregister it
				Datastore.unregister(regId);
			} else {
				logger.severe("Error sending message to device " + regId + ": "
						+ error);
			}
		}
	}

	// 全端末にメッセージを送信する場合の処理
	private void sendMulticastMessage(String multicastKey,
			HttpServletResponse resp) {
		// Recover registration ids from datastore
		List<String> regIds = Datastore.getMulticast(multicastKey);// データストアからレジストレーションIDを取得します。？

		//		Message message = new Message.Builder().build();

		Message.Builder builder = new Message.Builder();
		builder.addData("INDEX", String.valueOf(updateIndex.size())); // 更新された件数です。
		for (int i = 0; i < updateIndex.size(); i++) {

			builder.addData("TITLE"+(i+1), TITLE.get(updateIndex.get(i))); // 送信する件名データです。
			builder.addData("URL"+(i+1), LINK.get(updateIndex.get(i))); // 送信するURLデータです。
			builder.addData("ICON"+(i+1), ICON.get(updateIndex.get(i))); // 送信するICONデータです。
		}

		Message message = builder.build();

		MulticastResult multicastResult;
		try {
			multicastResult = sender.sendNoRetry(message, regIds);
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Exception posting " + message, e);
			multicastDone(resp, multicastKey);
			return;
		}
		boolean allDone = true;
		// check if any registration id must be updated
		if (multicastResult.getCanonicalIds() != 0) {// 送信に成功したレジストレーションIDの数を取得します。
			List<Result> results = multicastResult.getResults();// 個々の送信結果をリスト形式で返します。
			for (int i = 0; i < results.size(); i++) {
				String canonicalRegId = results.get(i)
						.getCanonicalRegistrationId();// 更新する必要があるIDがあれば返します。
				if (canonicalRegId != null) {
					String regId = regIds.get(i);
					Datastore.updateRegistration(regId, canonicalRegId);
				}
			}
		}
		if (multicastResult.getFailure() != 0) {// 送信が失敗していたケースが存在する場合の処理です。
			// there were failures, check if any could be retried
			List<Result> results = multicastResult.getResults();
			List<String> retriableRegIds = new ArrayList<String>();

			int error_count = 0;
			for (int i = 0; i < results.size(); i++) {
				String error = results.get(i).getErrorCodeName();
				if (error != null) {
					error_count++;
					String regId = regIds.get(i);
					logger.warning("Got error (" + error + ") for regId " + regId);
					if (error.equals(Constants.ERROR_NOT_REGISTERED)) {// NotRegistered
						// application has been removed from device - unregister
						// it
						Datastore.unregister(regId);
					}
					if (error.equals(Constants.ERROR_UNAVAILABLE)) {// Unavailable
						retriableRegIds.add(regId);
					}
				}
			}
			logger.warning("送信エラー >>" + error_count + "回");

			if (!retriableRegIds.isEmpty()) {// リトライするべきIDが存在した場合の処理です。
				// update task
				Datastore.updateMulticast(multicastKey, retriableRegIds);
				allDone = false;
				retryTask(resp);
			}
		}
		if (allDone) {
			multicastDone(resp, multicastKey);
		} else {
			retryTask(resp);
		}
	}

	private void multicastDone(HttpServletResponse resp, String encodedKey) {
		Datastore.deleteMulticast(encodedKey);
		taskDone(resp);
	}

}
