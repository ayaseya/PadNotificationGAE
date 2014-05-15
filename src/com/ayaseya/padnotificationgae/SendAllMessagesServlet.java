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

import static com.google.appengine.api.taskqueue.TaskOptions.Builder.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TaskOptions.Method;

/**
 * Servlet that adds a new message to all registered devices.
 * <p>
 * This servlet is used just by the browser (i.e., not device).
 */
// 登録されている全てのデバイスにメッセージを送信するためのサーブレットです。
@SuppressWarnings("serial")
public class SendAllMessagesServlet extends BaseServlet {

	private final Logger logger = Logger.getLogger(getClass().getName());

	
	/**
	 * Processes the request to add a new message.
	 */
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws IOException, ServletException {
		
		logger.info("SendAllMessagesServletが呼び出されました");
		
		/**
		 * 登録device数が増加した場合、データストアから読み込む処理の部分も
		 * タスクキューで実行しなければならない？
		 */
		List<String> devices = Datastore.getDevices();// リストに登録済みのレジストレーションIDを取得する。
		String status;
		if (devices.isEmpty()) {// レジストレーションIDが一つも登録されていなかった場合の処理
			status = "Message ignored as there is no device registered!";
		} else {// レジストレーションIDが登録されていた場合の処理
			// QueueFactoryクラスのgetQueueメソッドで名前(ここではgcm)を指定してキューを取得します(queue.xmlで定義した名前です)
			Queue queue = QueueFactory.getQueue("gcm");
			// NOTE: check below is for demonstration purposes; a real
			// application
			// could always send a multicast, even for just one recipient
			// 登録されているデバイス数が1つだった場合の処理
			if (devices.size() == 1) {
				// send a single message using plain post
				// レジストレーションIDを取得します。
				String device = devices.get(0);
				// タスクキューにパラメーターを設定し実行します。
				queue.add(withUrl("/send")
						.param(SendMessageServlet.PARAMETER_DEVICE, device));
				
				status = "Single message queued for registration id " + device;
				
				logger.info("SendAllMessagesServlet…)"+status.toString());
				
			} else {// 登録されているデバイスが複数だった場合の処理
				// send a multicast message using JSON
				// must split in chunks of 1000 devices (GCM limit)
				// デバイス数を取得します。
				int total = devices.size();
				// デバイス数分のリストのインスタンスを生成します。
				List<String> partialDevices = new ArrayList<String>(total);
				int counter = 0;
				int tasks = 0;
				for (String device : devices) {// 拡張for文内でタスクキューを次々と実行（予約）していきます。
					counter++;
					partialDevices.add(device);// リストにレジストレーションIDを格納します。
					int partialSize = partialDevices.size();
					if (partialSize == Datastore.MULTICAST_SIZE
							|| counter == total) {// 上限（1000件）に達した、もしくはカウンターが全てのデバイス登録数と一致したかを確認します。
						String multicastKey = Datastore
								.createMulticast(partialDevices);// エンコードされたキー（プライマリーキー）を取得します。
						logger.fine("Queuing " + partialSize
								+ " devices on multicast " + multicastKey);
						// タスクを呼び出すインスタンスを生成します。パラメータ等は自由に設定できます。
						TaskOptions taskOptions = TaskOptions.Builder
								.withUrl("/send")
								.param(SendMessageServlet.PARAMETER_MULTICAST,multicastKey).method(Method.POST);
						queue.add(taskOptions);// タスクキューに処理が登録され、非同期に実行されます。
						partialDevices.clear();// リストの全要素を削除します。
						tasks++;
					}
				}
				status = "Queued tasks to send " + tasks
						+ " multicast messages to " + total + " devices";
				logger.info("SendAllMessagesServlet…)"+status.toString());
			}
		}

		// requestスコープのデータを登録します。（スコープとは、データの有効範囲のことです。）
		req.setAttribute(HomeServlet.ATTRIBUTE_STATUS, status.toString());
		// homeのページにフォワード（遷移）します。
		getServletContext().getRequestDispatcher("/home").forward(req, resp);
	}

}
