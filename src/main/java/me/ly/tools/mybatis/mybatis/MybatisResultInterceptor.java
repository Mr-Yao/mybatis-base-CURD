package me.ly.tools.mybatis.mybatis;

import me.ly.tools.mybatis.mybatis.annotation.ResultIntercept;
import me.ly.tools.mybatis.utils.ReflectUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.*;

/**
 * Mybatis 查询结果集处理拦截器
 *
 * @author Created by LiYao on 2017-03-03 22:38.
 */
@SuppressWarnings({ "JavaDoc", "unused" })
@Intercepts({ @Signature(method = "handleResultSets", type = ResultSetHandler.class, args = { Statement.class }) })
public class MybatisResultInterceptor implements Interceptor {

    private boolean interceptAllMethod = true;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        ResultSetHandler resultSetHandler = (ResultSetHandler) invocation.getTarget();
        MappedStatement mappedStatement = (MappedStatement) ReflectUtil.getFieldValue(resultSetHandler, "mappedStatement");
        if (null == mappedStatement) {
            return invocation.proceed();
        }
        // 方法全限定名
        final String mappedId = mappedStatement.getId();
        if (null == mappedId || !isIntercept(mappedId)) {
            return invocation.proceed();
        }

        final Class<?> returnTypeClass = confirmReturnType(resultSetHandler, mappedStatement);
        if (returnTypeClass == null) {
            return invocation.proceed();
        }

        Statement statement = (Statement) invocation.getArgs()[0]; // 取得方法的参数Statement
        ResultSet rs = statement.getResultSet(); // 取得结果集

        List<ResultColumn> resultColumnList = confirmResultColumn(returnTypeClass, rs);
        if (resultColumnList.isEmpty()) {
            return invocation.proceed();
        }

        List<Object> list = new ArrayList<>();

        while (rs.next()) {
            Object obj = returnTypeClass.newInstance();
            for (ResultColumn resultColumn : resultColumnList) {
                Object o = getResultValue(resultColumn.getFieldType(), resultColumn.getColumnName(), rs);
                ReflectUtil.setFieldValue(obj, resultColumn.getFieldName(), o);
            }
            list.add(obj);
        }
        return list;
    }

    /**
     * 确认返回值类型
     *
     * @param resultSetHandler
     * @param mappedStatement
     * @return
     */
    private Class<?> confirmReturnType(ResultSetHandler resultSetHandler, MappedStatement mappedStatement) {
        DefaultParameterHandler defaultParameterHandler = (DefaultParameterHandler) ReflectUtil.getFieldValue(resultSetHandler,
                "parameterHandler");
        if (null == defaultParameterHandler) {
            return null;
        }
        Class<?> pojoClazz = null;

        //第一步判断参数中有无返回类型
        Map<?, ?> map = (Map<?, ?>) defaultParameterHandler.getParameterObject();
        if (map != null && map.containsKey("returnTypeClass")) {
            pojoClazz = (Class<?>) map.get("returnTypeClass");
        }
        if (pojoClazz != null) {
            return pojoClazz;
        }

        List<ResultMap> resultMaps = mappedStatement.getResultMaps();
        if (CollectionUtils.isEmpty(resultMaps) || resultMaps.size() > 1) {
            return null;
        }
        ResultMap resultMap = resultMaps.get(0);
        // 表示在XML中配置了 resultMap ，让Mybatis自己处理
        if (CollectionUtils.isNotEmpty(resultMap.getResultMappings())) {
            return null;
        }
        pojoClazz = resultMap.getType();
        // 如果没有配置该注解，则让Mybatis自己处理
        if (!pojoClazz.isAnnotationPresent(Table.class) && !pojoClazz.isAnnotationPresent(Entity.class)) {
            return null;
        }
        return pojoClazz;
    }

    /**
     * 确定返回结果字段
     *
     * @param returnTypeClass
     * @param resultSet
     * @return
     * @throws Exception
     */
    private List<ResultColumn> confirmResultColumn(final Class<?> returnTypeClass, final ResultSet resultSet) throws Exception {
        List<Field> fieldList = MyBatisUtil.getFieldContainsParent(returnTypeClass);

        ResultSetMetaData metaData = resultSet.getMetaData();
        final int columnCount = metaData.getColumnCount();

        Map<String, String> columnMap = new HashMap<>((int) (columnCount / 0.75));

        for (int i = 1; i <= columnCount; i++) {
            String name = metaData.getColumnLabel(i);
            columnMap.put(name, name);
        }

        List<ResultColumn> resultColumnList = new ArrayList<>();

        for (Field f : fieldList) {
            f.setAccessible(true);
            if (!f.isAnnotationPresent(Id.class) && !f.isAnnotationPresent(Column.class)) {
                continue;
            }
            String columnName = f.getName();
            if (null != f.getAnnotation(Column.class)) {
                Column col = f.getAnnotation(Column.class);
                String cn = col.name();
                if (cn.trim().length() > 0) {
                    columnName = cn;
                }
            }
            if (columnMap.containsKey(columnName)) {
                resultColumnList.add(new ResultColumn(columnName, f.getName(), f.getGenericType()));
            }
        }
        return resultColumnList;
    }
    // private Class<?>[] objectTypes = { Byte.class, Short.class,
    // Integer.class, Long.class, Double.class, Float.class, Boolean.class,
    // Date.class, BigDecimal.class, String.class };

    private Class<?>[] basicTypes = { byte.class, short.class, int.class, long.class, double.class, float.class, boolean.class };

    /**
     * 获取返回结果值
     *
     * @param resultType
     * @param fieldName
     * @param rs
     * @return
     * @throws Exception
     */
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

    private boolean isIntercept(String fullMethodName) throws Exception {
        // 方法名为空，不拦截
        if (StringUtils.isBlank(fullMethodName)) {
            return false;
        }

        int lastPointIndex = fullMethodName.lastIndexOf(".");
        String fullClazz = fullMethodName.substring(0, lastPointIndex);
        String methodName = fullMethodName.substring(lastPointIndex + 1, fullMethodName.length());
        Class<?> clazz = Class.forName(fullClazz);

        Method method = ReflectUtil.getMethod(clazz, methodName);
        ResultIntercept methodAnnotation = method.getAnnotation(ResultIntercept.class);
        if (methodAnnotation != null) {
            return methodAnnotation.intercept();
        }
        ResultIntercept classAnnotation = clazz.getAnnotation(ResultIntercept.class);
        if (interceptAllMethod) {
            // 设置了拦截所有方法的时候要判断接口类是不是配置不拦截。
            return !(classAnnotation != null && !classAnnotation.intercept());
        }
        // 未设置拦截所有方法，则判断接口类是不是配置了拦截
        return !(classAnnotation == null || !classAnnotation.intercept());
    }

    @SuppressWarnings("unused")
    public void setInterceptAllMethod(boolean interceptAllMethod) {
        this.interceptAllMethod = interceptAllMethod;
    }

    /**
     * 结果集字段
     */
    private static class ResultColumn {

        private String columnName;
        private String fieldName;
        private Type fieldType;

        ResultColumn(String columnName, String fieldName, Type fieldType) {
            this.columnName = columnName;
            this.fieldName = fieldName;
            this.fieldType = fieldType;
        }

        String getColumnName() {
            return columnName;
        }

        String getFieldName() {
            return fieldName;
        }

        Type getFieldType() {
            return fieldType;
        }
    }

}
