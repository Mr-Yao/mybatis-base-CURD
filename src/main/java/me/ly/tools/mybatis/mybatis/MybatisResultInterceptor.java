package me.ly.tools.mybatis.mybatis;

import me.ly.tools.mybatis.utils.ReflectUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;

import javax.persistence.Column;
import javax.persistence.Id;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

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
	private volatile String[] interceptMethods = null;

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
				Type resultType = f.getGenericType();
				String fieldName = f.getName();
				if (null != f.getAnnotation(Column.class)) {
					Column col = f.getAnnotation(Column.class);
					String cn = col.name();
					if (cn.trim().length() > 0) {
						fieldName = cn;
					}
				}
				Object o = getResultValue(resultType, fieldName, rs);
				ReflectUtil.setFieldValue(obj, f.getName(), o);
			}
			list.add(obj);
		}
		return list;
	}

	// private Class<?>[] objectTypes = { Byte.class, Short.class,
	// Integer.class, Long.class, Double.class, Float.class, Boolean.class,
	// Date.class, BigDecimal.class, String.class };

	private Class<?>[] basicTypes = { byte.class, short.class, int.class, long.class, double.class, float.class, boolean.class };

	private Object getResultValue(Type resultType, String fieldName, ResultSet rs) throws Exception {
		int index = rs.findColumn(fieldName);
		Object obj = rs.getObject(index);
		// 如果返回值要求不是基本数据类型 并且 在数据库对应值为null。直接返回null
		if (!arrayContains(basicTypes, resultType) && obj == null) {
			return null;
		}

		if (resultType.equals(String.class)) {
			return rs.getString(index);
		} else if (resultType.equals(byte.class) || resultType.equals(Byte.class)) {
			// 返回值要求是基本数据类型 并且 在数据库对应值为null。则返回默认值
			if (obj == null) {
				return 0;
			}
			return rs.getByte(index);
		} else if (resultType.equals(short.class) || resultType.equals(Short.class)) {
			if (obj == null) {
				return 0;
			}
			return rs.getShort(index);
		} else if (resultType.equals(int.class) || resultType.equals(Integer.class)) {
			if (obj == null) {
				return 0;
			}
			return rs.getInt(index);
		} else if (resultType.equals(long.class) || resultType.equals(Long.class)) {
			if (obj == null) {
				return 0L;
			}
			return rs.getLong(index);
		} else if (resultType.equals(double.class) || resultType.equals(Double.class)) {
			if (obj == null) {
				return 0.0D;
			}
			return rs.getDouble(index);
		} else if (resultType.equals(float.class) || resultType.equals(Float.class)) {
			if (obj == null) {
				return 0.0F;
			}
			return rs.getFloat(index);
		} else if (resultType.equals(boolean.class) || resultType.equals(Boolean.class)) {
			return obj != null && rs.getBoolean(index);
		} else if (resultType.equals(Date.class)) {
			return rs.getTimestamp(index);
		} else if (resultType.equals(BigDecimal.class)) {
			return rs.getBigDecimal(index);
		} else {
			return obj;
		}
	}

	private boolean arrayContains(Object[] arrays, Object obj) {
		if (arrays == null || arrays.length <= 0) {
			return false;
		}
		for (Object o : arrays) {
			if (o.equals(obj)) {
				return true;
			}
		}
		return false;
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
		if (this.interceptMethods == null) {
			this.interceptMethods = interceptMethods;
		}
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
