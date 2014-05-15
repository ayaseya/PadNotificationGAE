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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Transaction;

/**
 * Simple implementation of a data store using standard Java collections.
 * <p>
 * This class is neither persistent (it will lost the data when the app is
 * restarted) nor thread safe.
 */
public final class Datastore {

	static final int MULTICAST_SIZE = 1000;// 1回のクエリで取得できるエンティティ数は1000件までという制限に関係している？
	// カインド名（テーブル名）を設定します。Device
	// キー（プライマリーキー）の文字列となります。Device(1),Device(2),Device(3)…
	private static final String DEVICE_TYPE = "Device";
	private static final String DEVICE_REG_ID_PROPERTY = "regId";

	private static final String MULTICAST_TYPE = "Multicast";
	private static final String MULTICAST_REG_IDS_PROPERTY = "regIds";

	// FetchOptionsでデータストアのクエリ結果を取得する際にどのような方法を使用するか設定できます。
	// prefetchSizeは初回のアクセスで取得する件数
	// chunkSizeは2回目以降のアクセスで取得する件数
	private static final FetchOptions DEFAULT_FETCH_OPTIONS = FetchOptions.Builder
			.withPrefetchSize(MULTICAST_SIZE).chunkSize(MULTICAST_SIZE);

	private static final Logger logger = Logger.getLogger(Datastore.class.getName());
	
	// データストアにアクセスするためデータストアサービスのインスタンスを取得します。
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	// Datastoreクラスのコンストラクタ
	private Datastore() {
		// サブクラスからのコールを禁止します。
		throw new UnsupportedOperationException();
	}

	/**
	 * Registers a device.
	 * 
	 * @param regId
	 *            device's registration id.
	 */
	// レジストレーションIDを登録する処理です。
	public static void register(String regId) {
		logger.info("Registering " + regId);
		// トランザクション処理を開始します。
		// (データベースにおける)トランザクションとは、データベースへのデータの保存、取得、更新など一連の処理を、一つの処理として扱うことです。
		Transaction txn = datastore.beginTransaction();
		try {
			// データストアにレジストレーションIDが登録されているか確認します。
			Entity entity = findDeviceByRegId(regId);
			if (entity != null) {
				logger.fine(regId + " is already registered; ignoring.");
				return;
			}
			// 登録されていなかった場合、
			// カインド名を引数にエンティティのインスタンスを作成します。
			entity = new Entity(DEVICE_TYPE);
			// setProperty()メソッドの第一引数はプロパティー名、第２引数はプロパティーの値そのものです。
			entity.setProperty(DEVICE_REG_ID_PROPERTY, regId);// レジストレーションIDというプロパティ（カラム）とその要素を格納します。
			datastore.put(entity);// データストアに格納します。
			txn.commit();// コミット…トランザクション処理の確定します。（トランザクションの処理は終了します）
		} finally {
			if (txn.isActive()) {// コミットが失敗していたらトランザクションが終了せず生存しているのでロールバック処理に移ります。
				txn.rollback();// ロールバック…トランザクション中の処理の取り消します。
			}
		}
	}

	/**
	 * Unregisters a device.
	 * 
	 * @param regId
	 *            device's registration id.
	 */
	// レジストレーションIDの登録を解除する処理です。
	public static void unregister(String regId) {
		logger.info("Unregistering " + regId);
		Transaction txn = datastore.beginTransaction();
		try {
			Entity entity = findDeviceByRegId(regId);
			if (entity == null) {
				logger.warning("Device " + regId + " already unregistered");
			} else {// データストアに登録された、エンティティ（レコード）を削除します。
				Key key = entity.getKey();// 該当のレジストレーションIDのキー（プライマリー）を取得します。
				datastore.delete(key);// 該当のキーの値をデータストアから削除します。
			}
			txn.commit();
		} finally {
			if (txn.isActive()) {
				txn.rollback();
			}
		}
	}

	/**
	 * Updates the registration id of a device.
	 */
	// レジストレーションIDを更新する処理です。
	public static void updateRegistration(String oldId, String newId) {
		logger.info("Updating " + oldId + " to " + newId);
		Transaction txn = datastore.beginTransaction();
		try {
			Entity entity = findDeviceByRegId(oldId);
			if (entity == null) {
				logger.warning("No device for registration id " + oldId);
				return;
			}
			entity.setProperty(DEVICE_REG_ID_PROPERTY, newId);
			datastore.put(entity);
			txn.commit();
		} finally {
			if (txn.isActive()) {
				txn.rollback();
			}
		}
	}

	/**
	 * Gets all registered devices.
	 */
	// 現在登録されている全てのレジストレーションIDを取得しリスト形式で返す処理です。
	public static List<String> getDevices() {
		logger.info("getDevices()");
		List<String> devices;
		Transaction txn = datastore.beginTransaction();
		try {
			// キーを取得したいエンティティーが格納されているカインド名を引数に、Queryクラスのインスタンスを生成しています。
			Query query = new Query(DEVICE_TYPE);
			// Iterableインタフェースを実装すると、オブジェクトを「foreach」文の対象にすることができます。
			// foreach文（フォーイーチ文）とはプログラミング言語においてリストやハッシュテーブルなどの
			// データ構造の各要素に対して与えられた文の実行を繰り返すというループを記述するための文です。
			Iterable<Entity> entities = datastore.prepare(query).asIterable(DEFAULT_FETCH_OPTIONS);
			devices = new ArrayList<String>();
			
			for (Entity entity : entities) {
				String device = (String) entity
						.getProperty(DEVICE_REG_ID_PROPERTY);
				devices.add(device);
			}
			txn.commit();
		} finally {
			if (txn.isActive()) {
				txn.rollback();
			}
		}
		return devices;
	}

	/**
	 * Gets the number of total devices.
	 */
	// 現在登録されているデバイス数を返す処理です。
	public static int getTotalDevices() {
		logger.info("getTotalDevices()");
		Transaction txn = datastore.beginTransaction();
		try {
			Query query = new Query(DEVICE_TYPE).setKeysOnly();
			List<Entity> allKeys = datastore.prepare(query).asList(
					DEFAULT_FETCH_OPTIONS);
			int total = allKeys.size();
			logger.fine("Total number of devices: " + total);
			txn.commit();
			return total;
		} finally {
			if (txn.isActive()) {
				txn.rollback();
			}
		}
	}

	// レジストレーションIDから一致するエンティティ（レコード(行)）を返す処理です。？
	private static Entity findDeviceByRegId(String regId) {
		logger.info("findDeviceByRegId()");
		Query query = new Query(DEVICE_TYPE).addFilter(DEVICE_REG_ID_PROPERTY,
				FilterOperator.EQUAL, regId);
		// Queryクラスからエンティティーを取得するには、QueryのインスタンスからPreparedQueryクラスのインスタンスを生成します。
		PreparedQuery preparedQuery = datastore.prepare(query);
		List<Entity> entities = preparedQuery.asList(DEFAULT_FETCH_OPTIONS);// 1000件超えた時はどうなる？→ダミーデータを2000入れた時は特に問題なかった

		//		for(Entity empEntity : preparedQuery.asIterable()){// 1000件以上取得する場合には拡張for分で全件取得する？
		//		     System.out.println(
		//		          empEntity.getKind() + " - " + empEntity.getKey() );
		//		}

		Entity entity = null;
		if (!entities.isEmpty()) {
			entity = entities.get(0);
		}
		int size = entities.size();
		if (size > 0) {
			logger.severe("Found " + size + " entities for regId " + regId
					+ ": " + entities);
		}
		return entity;
	}

	/**
	 * Creates a persistent record with the devices to be notified using a
	 * multicast message.
	 * 
	 * @param devices
	 *            registration ids of the devices.
	 * @return encoded key for the persistent record.
	 */

	// http://docs.oracle.com/javase/jp/6/api/java/net/MulticastSocket.html
	// https://developers.google.com/appengine/docs/java/javadoc/com/google/appengine/api/datastore/KeyFactory#keyToString(com.google.appengine.api.datastore.Key)
	public static String createMulticast(List<String> devices) {// 引数は全てのレジストレーションIDです。
		logger.info("createMulticast()");
		logger.info("Storing multicast for " + devices.size() + " devices");
		String encodedKey;
		Transaction txn = datastore.beginTransaction();
		try {
			Entity entity = new Entity(MULTICAST_TYPE);
			entity.setProperty(MULTICAST_REG_IDS_PROPERTY, devices);// レジストレーションIDというプロパティ（カラム）とその要素を格納します。？
			datastore.put(entity);
			Key key = entity.getKey();// 該当のエンティティを示すキー（プライマリキー）を返します。
			encodedKey = KeyFactory.keyToString(key);// keyを指定してWebセーフ文字列表現に変換します。
			logger.fine("multicast key: " + encodedKey);
			txn.commit();
		} finally {
			if (txn.isActive()) {
				txn.rollback();
			}
		}
		return encodedKey;
	}

	/**
	 * Gets a persistent record with the devices to be notified using a
	 * multicast message.
	 * 
	 * @param encodedKey
	 *            encoded key for the persistent record.
	 */
	public static List<String> getMulticast(String encodedKey) {
		logger.info("getMulticast()");
		Key key = KeyFactory.stringToKey(encodedKey);
		Entity entity;
		Transaction txn = datastore.beginTransaction();
		try {
			entity = datastore.get(key);
			@SuppressWarnings("unchecked")
			List<String> devices = (List<String>) entity
					.getProperty(MULTICAST_REG_IDS_PROPERTY);
			txn.commit();
			return devices;
		} catch (EntityNotFoundException e) {
			logger.severe("No entity for key " + key);
			return Collections.emptyList();
		} finally {
			if (txn.isActive()) {
				txn.rollback();
			}
		}
	}

	/**
	 * Updates a persistent record with the devices to be notified using a
	 * multicast message.
	 * 
	 * @param encodedKey
	 *            encoded key for the persistent record.
	 * @param devices
	 *            new list of registration ids of the devices.
	 */
	public static void updateMulticast(String encodedKey, List<String> devices) {
		logger.info("updateMulticast()");
		Key key = KeyFactory.stringToKey(encodedKey);
		Entity entity;
		Transaction txn = datastore.beginTransaction();
		try {
			try {
				entity = datastore.get(key);
			} catch (EntityNotFoundException e) {
				logger.severe("No entity for key " + key);
				return;
			}
			entity.setProperty(MULTICAST_REG_IDS_PROPERTY, devices);
			datastore.put(entity);
			txn.commit();
		} finally {
			if (txn.isActive()) {
				txn.rollback();
			}
		}
	}

	/**
	 * Deletes a persistent record with the devices to be notified using a
	 * multicast message.
	 * 
	 * @param encodedKey
	 *            encoded key for the persistent record.
	 */
	public static void deleteMulticast(String encodedKey) {
		logger.info("deleteMulticast()");
		Transaction txn = datastore.beginTransaction();
		try {
			Key key = KeyFactory.stringToKey(encodedKey);
			datastore.delete(key);
			txn.commit();
		} finally {
			if (txn.isActive()) {
				txn.rollback();
			}
		}
	}

}
