package me.ly.tools.mybatis.mybatis;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.ly.tools.mybatis.entity.Pagination;
import me.ly.tools.mybatis.utils.ReflectUtil;

/**
 * Mybatis分页拦截组件
 *
 * @author Created by LiYao on 2017-03-04 21:16.
 */
@Intercepts({ @Signature(method = "prepare", type = StatementHandler.class, args = { Connection.class }) })
@SuppressWarnings({ "unused", "SqlDialectInspection", "SqlNoDataSourceInspection" })
public class MybatisPaginationInterceptor implements Interceptor {

	private static Logger logger = LoggerFactory.getLogger(MybatisPaginationInterceptor.class);

	@Override
	public Object intercept(Invocation invocation) throws Throwable {
		RoutingStatementHandler handler = (RoutingStatementHandler) invocation.getTarget();
		StatementHandler delegate = (StatementHandler) ReflectUtil.getFieldValue(handler, "delegate");
		if (null == delegate) {
			return invocation.proceed();
		}
		BoundSql boundSql = delegate.getBoundSql();
		// 拿到传入的参数分页实体类
		Object obj = boundSql.getParameterObject();
		Object pagParme = obj;
		// 获取当前要执行的Sql语句，也就是我们直接在Mapper映射语句中写的Sql语句
		String sql = MyBatisUtil.ridSqlBlank(boundSql.getSql());

		// 用于处理insert时，将返回的key值与@Id标注的字段对应
		if (sql.toUpperCase().startsWith("INSERT")) {
			keyProperties(delegate, pagParme);
			return invocation.proceed();
		}
		// 只处理SELECT语句
		if (!sql.toUpperCase().startsWith("SELECT")) {
			return invocation.proceed();
		}
		boolean isPaging = false;

		if (obj instanceof Map<?, ?>) {
			Map<?, ?> paramMap = (Map<?, ?>) obj;
			for (Map.Entry<?, ?> entry : paramMap.entrySet()) {
				Object val = entry.getValue();
				if (val instanceof Pagination) {
					pagParme = val;
					isPaging = true;
					break;
				}
			}
		}

		if (obj instanceof Pagination || isPaging) {
			Pagination pagination = (Pagination) pagParme;
			// 拦截到的prepare方法参数是一个Connection对象
			Connection connection = (Connection) invocation.getArgs()[0];

			this.setTotalRecord(sql, connection, pagination, obj);

			// 获取分页Sql语句
			String pageSql = this.getPageSql(pagination, sql, connection);

			// 利用反射设置当前BoundSql对应的sql属性为我们建立好的分页Sql语句
			ReflectUtil.setFieldValue(boundSql, "sql", pageSql);

		}

		return invocation.proceed();
	}

	@Override
	public Object plugin(Object target) {
		return Plugin.wrap(target, this);
	}

	@Override
	public void setProperties(Properties properties) {
		System.out.println(properties.getProperty("databaseType"));
	}

	private void keyProperties(StatementHandler delegate, Object paramObj) {
		MappedStatement mappedStatement = (MappedStatement) ReflectUtil.getFieldValue(delegate, "mappedStatement");
		if (mappedStatement == null) {
			return;
		}
		// 方法全限定名
		String mappedId = mappedStatement.getId();
		// 考虑到其它类中也可能定义insert方法，且不一定会使用@Id或自己配置了keyProperties。
		// 所以只拦截封装的基本insert方法，该方法在BaseMapper中，由GeneralMapper继承
		if (StringUtils.isEmpty(mappedId) || !mappedId.endsWith("GeneralMapper.insert")) {
			return;
		}
		Map<String, String> idMap = ReflectUtil.id(paramObj.getClass());
		String[] strs = { idMap.get(MyBatisUtil.KEY_ID_FIELD) };
		ReflectUtil.setFieldValue(mappedStatement, "keyProperties", strs);
	}

	/**
	 * 给当前的参数对象page设置总记录数
	 * 
	 * @param originalSql
	 *            原始SQL
	 * @param connection
	 *            数据库连接
	 * @param page
	 *            分页对象
	 * @param paramObj
	 *            参数对象
	 * 
	 */
	private void setTotalRecord(String originalSql, Connection connection, Pagination page, Object paramObj) {

		PreparedStatement preparedStatement = null;
		ResultSet rs = null;
		try {
			// String countSql = "SELECT COUNT(*) AS TOTAL FROM (" + originalSql
			// + ") AS A";

			int fromIndex = originalSql.toUpperCase().indexOf("FROM");

			String countSql = "SELECT COUNT(*) AS TOTAL " + originalSql.substring(fromIndex);
			preparedStatement = connection.prepareStatement(countSql);

			if (countSql.contains("?") && paramObj instanceof Map<?, ?>) {
				Map<?, ?> paramMap = (Map<?, ?>) paramObj;
				Object[] params = (Object[]) paramMap.get("params");
				if (params != null) {
					for (int i = 0; i < params.length; i++) {
						preparedStatement.setObject(i + 1, params[i]);
					}
				}

			}

			rs = preparedStatement.executeQuery();
			if (rs.next()) {
				int totalRecord = rs.getInt("TOTAL");
				page.setTotalCount(totalRecord);
			}
		} catch (SQLException e) {
			logger.error("", e);
		} finally {
			try {
				if (rs != null) {
					rs.close();
				}
				if (preparedStatement != null) {
					preparedStatement.close();
				}
			} catch (SQLException e) {
				logger.error("", e);
			}
		}
	}

	/**
	 * 根据page对象获取对应的分页查询Sql语句，这里只做了两种数据库类型，Mysql和Oracle 其它的数据库都 没有进行分页
	 *
	 * @param page
	 *            分页对象
	 * @param sql
	 *            原sql语句
	 * @param connection
	 *            数据库连接
	 * @return sql
	 */
	private String getPageSql(Pagination page, String sql, Connection connection) throws Exception {
		String dbType = connection.getMetaData().getDatabaseProductName().toUpperCase();
		StringBuffer sqlBuffer = new StringBuffer(sql);
		if ("MYSQL".equals(dbType)) {
			return getMysqlPageSql(page, sql, connection);
		} else if ("ORACLE".equals(dbType)) {
			return getOraclePageSql(page, sqlBuffer);
		}
		return sqlBuffer.toString();
	}

	/**
	 * 获取Mysql数据库的分页查询语句
	 *
	 * @param page
	 *            分页对象
	 * @param sql
	 *            原sql语句
	 * @return Mysql数据库分页语句
	 */
	private String getMysqlPageSql(Pagination page, String sql, Connection connection) throws Exception {
		if (MyBatisUtil.isJoinQuery(sql)) {
			return this.mysqlLimitPageSql(page, sql);
		} else {
			return this.mysqlSubqueryPageSql(page, sql, connection);
		}
	}

	/**
	 * Mysql数据库的Limit分页查询语句
	 *
	 * @param page
	 *            分页对象
	 * @param sql
	 *            原sql语句
	 * @return Mysql数据库Limit分页查询语句
	 */
	private String mysqlLimitPageSql(Pagination page, String sql) {
		StringBuilder sqlBuilder = new StringBuilder(sql);
		int offset = (page.getPageIndex() - 1) * page.getPageSize();
		sqlBuilder.append(" LIMIT ").append(offset).append(",").append(page.getPageSize());
		return sqlBuilder.toString();
	}

	/**
	 * Mysql数据库的子查询分页语句
	 * 
	 * @param page
	 *            分页对象
	 * @param sql
	 *            sql语句
	 * @param connection
	 *            数据库连接
	 * @return sql
	 * 
	 * @throws Exception
	 */
	@SuppressWarnings({ "StringBufferReplaceableByString", "JavaDoc" })
	private String mysqlSubqueryPageSql(Pagination page, String sql, Connection connection) throws Exception {

		String tableSet = MyBatisUtil.getTableSetFromSql(sql).trim();
		// 获取别名
		int blankIndex = tableSet.lastIndexOf(" ");
		String alias = "";
		String tableName = tableSet;
		if (blankIndex > -1) {
			alias = tableSet.substring(blankIndex);
			tableName = tableSet.substring(0, blankIndex);
		}
		String idName = this.getFirstPkName(tableName, connection);
		String selectSet = MyBatisUtil.getSelectSetFromSql(sql);
		String from = "";
		int fromIndex = sql.toUpperCase().indexOf("FROM ");
		if (fromIndex > -1) {
			from = sql.substring(fromIndex).replace(alias, "");
		}
		// 是否存在别名，不存在则添加别名
		if (StringUtils.isBlank(alias)) {
			alias = "t1";
			String[] strs = selectSet.split(",");
			for (int i = 0; i < strs.length; i++) {
				strs[i] = alias + "." + strs[i];
			}
			selectSet = StringUtils.join(strs, ",");
		}

		int offset = (page.getPageIndex() - 1) * page.getPageSize();

		/*
		 * SELECT company_name FROM test1 t1 JOIN (SELECT id as id FROM test1
		 * ORDER BY company_name limit 736817,20) as t2 ON t2.id = t1.id;
		 */
		StringBuilder sqlBuilder = new StringBuilder("SELECT ");
		sqlBuilder.append(selectSet).append(" FROM ").append(tableName).append(" ").append(alias);
		sqlBuilder.append(" JOIN ").append(" (SELECT ").append(idName).append(" AS id ");
		sqlBuilder.append(from).append(" LIMIT ").append(offset).append(",");
		sqlBuilder.append(page.getPageSize()).append(") t2 ON t2.id = t1.").append(idName);

		return sqlBuilder.toString();
	}

	/**
	 * 获取Oracle数据库的分页查询语句
	 *
	 * @param page
	 *            分页对象
	 * @param sqlBuffer
	 *            包含原sql语句的StringBuffer对象
	 * @return Oracle数据库的分页查询语句
	 */
	private String getOraclePageSql(Pagination page, StringBuffer sqlBuffer) {
		int offset = (page.getPageIndex() - 1) * page.getPageSize() + 1;
		sqlBuffer.insert(0, "SELECT U.*, ROWNUM r FROM (").append(") U WHERE ROWNUM < ").append(offset + page.getPageSize());
		sqlBuffer.insert(0, "SELECT * FROM (").append(") WHERE r >= ").append(offset);
		return sqlBuffer.toString();
	}

	private String getDbName(Connection connection) throws Exception {
		String url = connection.getMetaData().getURL();
		int end = url.indexOf("?");
		if (!url.contains("?")) {
			end = url.length();
		}
		return StringUtils.substring(url, url.lastIndexOf("/") + 1, end);
	}

	private String getFirstPkName(String table, Connection connection) throws Exception {
		ResultSet rs = connection.getMetaData().getPrimaryKeys(null, this.getDbName(connection), table);
		try {
			while (rs.next()) {
				// 第一个主键
				if (rs.getShort("KEY_SEQ") == 1) {
					return rs.getString("COLUMN_NAME");
				}
			}
			return null;
		} finally {
			if (rs != null) {
				rs.close();
			}
		}
	}
}
