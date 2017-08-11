package me.ly.tools.mybatis.mybatis;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.jdbc.SQL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.ly.tools.mybatis.utils.ReflectUtil;

/**
 * 拼装SQL条件的模板。Create、Retrieve、Update、Delete
 *
 * @author Created by LiYao on 2017-03-03 22:47.
 */
@SuppressWarnings({ "unused", "JavaDoc" })
public class CRUDTemplate {

	private final static Logger LOG = LoggerFactory.getLogger(CRUDTemplate.class);

	/**
	 * select sql
	 * 
	 * @param map
	 * @return
	 */
	public String select(Map<?, ?> map) {
		SQL sql = new SQL();
		sql.SELECT("*").FROM(ReflectUtil.tableName((Class<?>) map.get("clazz")));
		String where = (String) map.get("where");
		if (null == where || where.trim().length() <= 0) {
			return sql.toString();
		}

		// limit
		String limit = "";
		if (where.toUpperCase().contains("LIMIT")) {
			limit = where.toUpperCase().substring(where.toUpperCase().indexOf("LIMIT"), where.length());
			where = where.substring(0, where.toUpperCase().indexOf("LIMIT"));
		}

		// ORDER BY
		if (where.toUpperCase().contains("ORDER BY")) {
			String orderBy = where.substring(where.toUpperCase().indexOf("ORDER BY"), where.length());
			if (orderBy.trim().length() > 0) {
				sql.ORDER_BY(orderBy.replaceAll("ORDER BY", ""));
			}
			where = where.substring(0, where.toUpperCase().indexOf("ORDER BY"));
		}
		// GROUP BY
		if (where.toUpperCase().contains("GROUP BY")) {
			String groupBy = where.substring(where.toUpperCase().indexOf("GROUP BY"), where.length());
			if (groupBy.trim().length() > 0) {
				sql.GROUP_BY(groupBy.replaceAll("GROUP BY", ""));
			}
			where = where.substring(0, where.toUpperCase().indexOf("GROUP BY"));
		}

		if (where.trim().length() > 0) {
			sql.WHERE(refactorSql(where));
		}
		return sql.toString() + limit;
	}

	/**
	 * selectById sql
	 *
	 * @param map
	 * @return String
	 */
	public String selectById(Map<?, ?> map) {
		SQL sql = new SQL();
		Class<?> clazz = (Class<?>) map.get("clazz");
		sql.SELECT("*").FROM(ReflectUtil.tableName(clazz));
		Object id = map.get("id");
		if (null == id || "".equals(id)) {
			return sql.toString();
		}
		Map<String, String> columnMap = ReflectUtil.id(clazz);
		sql.WHERE(columnMap.get(MyBatisUtil.KEY_ID_COLUMN) + " = #{id}");
		return sql.toString();
	}

	/**
	 * insert sql
	 * 
	 * @param obj
	 * @return
	 */
	public String insert(Object obj) throws Exception {
		if (obj == null) {
			return "";
		}
		SQL sql = new SQL();
		sql.INSERT_INTO(ReflectUtil.tableName(obj.getClass()));

		Map<String, String> map = MyBatisUtil.insertColumns(obj);
		for (Map.Entry<String, String> m : map.entrySet()) {
			if (MyBatisUtil.KEY_ID_FIELD.equals(m.getKey())) {
				continue;
			}
			sql.VALUES(m.getKey(), "#{" + m.getValue() + "}");
		}
		return sql.toString();
	}

	/**
	 * insertList sql。 <br>
	 * 该方法目前仅支持“INSERT INTO user (column1,column2,column3) VALUES
	 * (?,?,?),(?,?,?)”Sql样式的数据库
	 *
	 * @param map
	 * @return
	 */
	public String insertList(Map<?, ?> map) throws Exception {
		List<?> list = (List<?>) map.get("list");
		if (list == null || list.isEmpty()) {
			return "";
		}

		Map<String, String> columnMap = MyBatisUtil.insertColumns(list.get(0));

		StringBuilder columnName = new StringBuilder();
		StringBuilder columnValue = new StringBuilder();

		for (Map.Entry<String, String> m : columnMap.entrySet()) {
			if (MyBatisUtil.KEY_ID_FIELD.equals(m.getKey())) {
				continue;
			}
			columnName.append(",").append(m.getKey());
			columnValue.append(",#{list[@_index-_].").append(m.getValue()).append("}");
		}

		StringBuilder sql = new StringBuilder();
		for (int i = 0; i < list.size(); i++) {
			sql.append(",(").append(columnValue.substring(1).replace("@_index-_", i + "")).append(")");
		}

		sql = sql.replace(0, 1, " ");
		sql.insert(0, "INSERT INTO %s (%s) VALUES");

		String tableName = ReflectUtil.tableName(list.get(0).getClass());
		return String.format(sql.toString(), tableName, columnName.substring(1));
	}

	/**
	 * 删除一个实体
	 *
	 * @param obj
	 * @return
	 */
	public String delete(Object obj) {
		Map<String, String> map = ReflectUtil.id(obj.getClass());
		SQL sql = new SQL();
		sql.DELETE_FROM(ReflectUtil.tableName(obj.getClass()));
		sql.WHERE(map.get(MyBatisUtil.KEY_ID_COLUMN) + " = #{" + map.get(MyBatisUtil.KEY_ID_FIELD) + "}");
		return sql.toString();
	}

	/**
	 * 更新一个实体,根据ID更新。
	 * 
	 * @param map
	 * @return
	 */
	public String update(Map<?, ?> map) throws Exception {
		SQL sql = new SQL();
		Object obj = map.get("bean");
		boolean ignoreNull = (Boolean) map.get("ignoreNull");
		boolean ignoreEmpty = (Boolean) map.get("ignoreEmpty");

		sql.UPDATE(ReflectUtil.tableName(obj.getClass()));

		Map<String, String> columnMap = MyBatisUtil.updateColumns(obj, ignoreNull, ignoreEmpty);

		for (Map.Entry<String, String> m : columnMap.entrySet()) {
			if (MyBatisUtil.KEY_ID_FIELD.equals(m.getKey()) || MyBatisUtil.KEY_ID_COLUMN.equals(m.getKey())) {
				continue;
			}
			sql.SET(m.getKey() + " = #{bean." + m.getValue() + "}");
		}

		sql.WHERE(columnMap.get(MyBatisUtil.KEY_ID_COLUMN) + " = #{bean." + columnMap.get(MyBatisUtil.KEY_ID_FIELD) + "}");
		return sql.toString();
	}

	/**
	 * 重构SQL。处理占位符
	 *
	 * @param map
	 * @return
	 */
	public String refactorSql(Map<?, ?> map) {
		String sql = (String) map.get("sql");
		return refactorSql(sql);

	}

	private String refactorSql(String sql) {
		if (!StringUtils.contains(sql, "?")) {
			return sql;
		}

		int start = sql.indexOf("?");
		int end = sql.lastIndexOf("?") + 1;
		String questionMark = sql.substring(start, end);

		String tmpStr = questionMark.trim();
		String[] strs = tmpStr.length() > 1 ? tmpStr.split("\\?") : new String[] { "" };

		StringBuilder sb = new StringBuilder(sql.length() + strs.length * 11 - strs.length);
		for (int i = 0; i < strs.length; i++) {
			sb.append(strs[i]).append("#{params[").append(i).append("]}");
		}

		return sql.replace(questionMark, sb.toString());

	}

}
