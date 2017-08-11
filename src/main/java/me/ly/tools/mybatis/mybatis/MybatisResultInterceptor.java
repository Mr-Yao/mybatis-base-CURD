package me.ly.tools.mybatis.mybatis;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

import javax.persistence.Column;
import javax.persistence.Id;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;

import me.ly.tools.mybatis.utils.ReflectUtil;

/**
 * Mybatis 查询结果集处理拦截器
 * 
 * @author Created by LiYao on 2017-03-03 22:38.
 */
@Intercepts({ @Signature(method = "handleResultSets", type = ResultSetHandler.class, args = { Statement.class }) })
@SuppressWarnings("unused")
public class MybatisResultInterceptor implements Interceptor {

	// private static Logger logger =
	// LoggerFactory.getLogger(MybatisResultInterceptor.class);
	// 需要拦截处理的方法
	private String[] interceptMethods = { "baseSelectAll", "baseSelectByPage", "baseSelectById" };

	@Override
	public Object intercept(Invocation invocation) throws Throwable {
		ResultSetHandler resultSetHandler = (ResultSetHandler) invocation.getTarget();
		MappedStatement mappedStatement = (MappedStatement) ReflectUtil.getFieldValue(resultSetHandler, "mappedStatement");
		if (null == mappedStatement) {
			return invocation.proceed();
		}
		// 方法全限定名
		String mappedId = mappedStatement.getId();

		if (null == mappedId) {
			return invocation.proceed();
		}
		// 判断是否拦截
		if (!isIntercept(mappedId)) {
			return invocation.proceed();
		}

		DefaultParameterHandler defaultParameterHandler = (DefaultParameterHandler) ReflectUtil.getFieldValue(resultSetHandler,
				"parameterHandler");
		if (null == defaultParameterHandler) {
			return invocation.proceed();
		}
		Map<?, ?> map = (Map<?, ?>) defaultParameterHandler.getParameterObject();
		Class<?> pojoClazz = (Class<?>) map.get("clazz");
		List<Field> fieldList = MyBatisUtil.getFieldContainsParent(pojoClazz);
		// Field[] fields = pojoClazz.getDeclaredFields();
		Statement statement = (Statement) invocation.getArgs()[0]; // 取得方法的参数Statement
		ResultSet rs = statement.getResultSet(); // 取得结果集
		List<Object> list = new ArrayList<>();
		while (rs.next()) {
			Object obj = pojoClazz.newInstance();
			for (Field f : fieldList) {
				f.setAccessible(true);
				if (!f.isAnnotationPresent(Id.class) && !f.isAnnotationPresent(Column.class)) {
					continue;
				}
				Type clazz = f.getGenericType();
				Object o;
				String fieldName = f.getName();
				if (null != f.getAnnotation(Column.class)) {
					Column col = f.getAnnotation(Column.class);
					String cn = col.name();
					if (cn.trim().length() > 0) {
						fieldName = cn;
					}
				}
				if (clazz.equals(int.class) || clazz.equals(Integer.class)) {
					o = rs.getInt(fieldName);
				} else if (clazz.equals(Date.class)) {
					o = rs.getTimestamp(fieldName);
				} else if (clazz.equals(boolean.class) || clazz.equals(Boolean.class)) {
					o = rs.getBoolean(fieldName);
				} else if (clazz.equals(byte.class) || clazz.equals(Byte.class)) {
					o = rs.getByte(fieldName);
				} else {
					o = rs.getObject(fieldName);
				}
				ReflectUtil.setFieldValue(obj, f.getName(), o);
			}
			list.add(obj);
		}
		return list;
	}

	@Override
	public Object plugin(Object target) {
		return Plugin.wrap(target, this);
	}

	@Override
	public void setProperties(Properties properties) {
		System.out.println(properties.getProperty("databaseType"));
	}

	@SuppressWarnings("unused")
	public void setInterceptMethods(String... interceptMethods) {
		this.interceptMethods = interceptMethods;
	}

	private boolean isIntercept(String method) {
		if (StringUtils.isBlank(method)) {
			return false;
		}
		if (interceptMethods == null || interceptMethods.length == 0) {
			return true;
		}

		for (String name : interceptMethods) {
			if (method.endsWith(name)) {
				return true;
			}
		}
		return false;
	}
}
