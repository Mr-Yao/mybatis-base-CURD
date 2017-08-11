package me.ly.tools.mybatis.mybatis;

import java.lang.reflect.Field;
import java.util.*;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;

import org.apache.commons.lang3.StringUtils;

/**
 * MyBatisUtil
 *
 * @author Created by LiYao on 2017-03-05 14:22.
 */
@SuppressWarnings({ "WeakerAccess", "JavaDoc" })
public class MyBatisUtil {

	/**
	 * The constant KEY_ID_COLUMN.
	 */
	public static final String KEY_ID_COLUMN = "@_idColumn-_";
	/**
	 * The constant KEY_ID_FIELD.
	 */
	public static final String KEY_ID_FIELD = "@_idField-_";

	/**
	 * 用于新增时属性与字段映射缓存 <br>
	 * Map&lt;Class, Map&lt;ColumnName, FiledName>><br>
	 */
	private static Map<Class<?>, Map<String, String>> insertColumnMap = new HashMap<>();

	/**
	 * 用于新增时属性与字段映射 <br>
	 * Map中定义了特殊Key:@_idField-_，代表obj中的ID字段名称<br>
	 *
	 * @param obj
	 *            the obj
	 * @return Map &lt;ColumnName, FiledName>
	 * @throws Exception
	 *             the exception
	 */
	public static <T> Map<String, String> insertColumns(T obj) throws Exception {
		Map<String, String> columnMap = insertColumnMap.get(obj.getClass());
		if (columnMap == null) {
			columnMap = new HashMap<>();
		} else {
			if (!columnMap.isEmpty()) {
				return columnMap;
			}
		}
		List<Field> fieldList = getFieldContainsParent(obj.getClass());
		for (Field field : fieldList) {
			field.setAccessible(true);
			if (field.isAnnotationPresent(Transient.class) || !field.isAnnotationPresent(Column.class)) {
				continue;
			}
			if (field.isAnnotationPresent(Id.class)) {
				if (field.get(obj) == null) {
					continue;
				}
				columnMap.put(KEY_ID_FIELD, field.getName());
			}
			Column column = field.getAnnotation(Column.class);
			if (column.insertable()) {
				columnMap.put(getColumnName(field), field.getName());
			}
		}
		insertColumnMap.put(obj.getClass(), columnMap);
		return columnMap;
	}

	/**
	 * 获取clazz中声明的字段，包含带MappedSuperclass注解的父类中声明的字段
	 * 
	 * @param clazz
	 * @param <T>
	 * @return
	 * @throws Exception
	 */
	public static <T> List<Field> getFieldContainsParent(Class<T> clazz) throws Exception {
		List<Field> fieldList = new ArrayList<>();
		parentFields(clazz, fieldList);
		fieldList.addAll(Arrays.asList(clazz.getDeclaredFields()));
		return fieldList;
	}

	private static <T> void parentFields(Class<T> childClass, List<Field> fieldList) {
		Class<?> clazz = childClass.getSuperclass();
		if (clazz.isAnnotationPresent(MappedSuperclass.class)) {
			parentFields(clazz, fieldList);
			fieldList.addAll(Arrays.asList(clazz.getDeclaredFields()));
		}
	}

	/**
	 * 用于新增时属性与字段映射（null或""字段除外） <br>
	 * Map中定义了特殊Key:@_idField-_，代表obj中的ID字段名称<br>
	 * Map中定义了特殊Key:@_idColumn-_，代表obj中的ID字段名称对应的数据库字段名称
	 *
	 * @param obj
	 *            the obj
	 * @return Map &lt;ColumnName, FiledName>
	 * @throws Exception
	 *             the exception
	 */
	public static <T> Map<String, String> updateColumns(T obj, boolean ignoreNull, boolean ignoreEmpty) throws Exception {
		Map<String, String> columnMap = new HashMap<>();
		List<Field> fieldList = getFieldContainsParent(obj.getClass());
		for (Field field : fieldList) {
			field.setAccessible(true);

			if (field.isAnnotationPresent(Id.class)) {
				columnMap.put(KEY_ID_COLUMN, getColumnName(field));
				columnMap.put(KEY_ID_FIELD, field.getName());
				continue;
			}

			if (!field.isAnnotationPresent(Column.class) || field.isAnnotationPresent(Transient.class)) {
				continue;
			}

			Object value = field.get(obj);

			// 是否忽略Null字段
			if (ignoreNull && value == null) {
				continue;
			}
			// 是否忽略""字段
			if (ignoreEmpty && "".equals(value)) {
				continue;
			}
			Column column = field.getAnnotation(Column.class);
			if (column.updatable()) {
				columnMap.put(getColumnName(field), field.getName());
			}
		}

		return columnMap;
	}

	private static String getColumnName(Field field) {
		Column column = field.getAnnotation(Column.class);
		if (column != null && !"".equals(column.name().trim())) {
			return column.name();
		}
		return field.getName();
	}

	/**
	 * 去掉SQL中多余的空格
	 *
	 * @param sql
	 * @return
	 */
	public static String ridSqlBlank(String sql) {
		String[] strs = sql.split(" ");
		List<String> list = new ArrayList<>(strs.length);
		for (String str : strs) {
			if (StringUtils.isBlank(str)) {
				continue;
			}
			list.add(str);
		}
		return StringUtils.join(list, " ");
	}

	/**
	 * SQL 是否是连接查询
	 *
	 * @param sql
	 * @return
	 */
	public static boolean isJoinQuery(String sql) {
		// SELECT * FROM table WHERE ...
		String tmpSql = sql.toUpperCase();
		return tmpSql.contains("JOIN ") || getTableSetFromSql(sql).contains(",");
	}

	/**
	 * 从SQL中获取表名
	 * 
	 * @param sql
	 * @return
	 */
	public static String getTableSetFromSql(String sql) {
		String tmpSql = sql;

		int orderByIndex = tmpSql.toUpperCase().indexOf("ORDER BY ");
		if (orderByIndex > -1) {
			// SELECT * FROM table ORDER BY... to SELECT * FROM table
			tmpSql = tmpSql.substring(0, orderByIndex);
		}

		int groupByIndex = tmpSql.toUpperCase().indexOf("GROUP BY ");
		if (groupByIndex > -1) {
			// SELECT * FROM table GROUP BY... to SELECT * FROM table
			tmpSql = tmpSql.substring(0, groupByIndex);
		}

		int fromIndex = tmpSql.toUpperCase().indexOf("FROM ");
		// SELECT * FROM table to table
		String tableSet = tmpSql.substring(fromIndex + 4);

		int whereIndex = tmpSql.toUpperCase().indexOf("WHERE ");
		if (whereIndex > -1) {
			// SELECT * FROM table WHERE ... to table
			tableSet = tmpSql.substring(fromIndex + 4, whereIndex);
		}
		return tableSet;
	}

	public static String getSelectSetFromSql(String sql) {
		String tmpSql = sql.toUpperCase();
		int fromIndex = tmpSql.indexOf("FROM ");
		return sql.substring(6, fromIndex).trim();
	}
}
