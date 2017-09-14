package me.ly.tools.mybatis.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.ly.tools.mybatis.mybatis.MyBatisUtil;

/**
 * 反射工具类
 *
 * @author Created by LiYao on 2017-03-04 21:10.
 */
@SuppressWarnings({ "JavaDoc", "WeakerAccess", "unused" })
public class ReflectUtil {

    private static Logger logger = LoggerFactory.getLogger(ReflectUtil.class);

    /**
     * 利用反射获取表名。需定义javax.persistence.@Table，如果未定义则返回类名
     * 
     * @param clazz
     * @return
     */
    public static String tableName(Class<?> clazz) {
        Table table = clazz.getAnnotation(Table.class);
        if (null != table && !"".equals(table.name().trim())) {
            return table.name();
        }
        logger.warn("{}未配置@Table,将使用类名作为对应的表名", clazz);
        return clazz.getSimpleName();
    }

    /**
     * 获取POJO中的主键字段名 需要定义javax.persistence.@Id <br>
     * Map中定义了特殊Key:@_idField-_，代表Clazz中的ID字段名称<br>
     * Map中定义了特殊Key:@_idColumn-_，代表Clazz中的ID字段名称对应的数据库字段名称
     *
     * 
     * @param clazz
     * @return
     */
    public static Map<String, String> id(Class<?> clazz) {
        Map<String, String> map = new HashMap<>();
        for (Field field : clazz.getDeclaredFields()) {
            if (!field.isAnnotationPresent(Id.class)) {
                continue;
            }
            map.put(MyBatisUtil.KEY_ID_FIELD, field.getName());
            Column column = field.getAnnotation(Column.class);
            if (column == null || "".equals(column.name().trim())) {
                map.put(MyBatisUtil.KEY_ID_COLUMN, field.getName());
                return map;
            }
            map.put(MyBatisUtil.KEY_ID_COLUMN, column.name());
            return map;

        }
        throw new RuntimeException("POJO 没有定义 @Id");
    }

    /**
     * 利用反射获取指定对象的指定属性
     *
     * @param obj 目标对象
     * @param fieldName 目标属性
     * @return 目标属性的值
     */
    public static Object getFieldValue(Object obj, String fieldName) {
        Object result = null;
        Field field = ReflectUtil.getField(obj, fieldName);
        if (null == field) {
            return null;
        }
        field.setAccessible(true);
        try {
            result = field.get(obj);
        } catch (Exception e) {
            logger.error("", e);
        }
        return result;
    }

    /**
     * 利用反射获取指定对象里面的指定属性
     *
     * @param obj 目标对象
     * @param fieldName 目标属性
     * @return 目标字段
     */
    public static Field getField(Object obj, String fieldName) {
        Field field = null;
        for (Class<?> clazz = obj.getClass(); clazz != Object.class; clazz = clazz.getSuperclass()) {
            try {
                field = clazz.getDeclaredField(fieldName);
                break;
            } catch (NoSuchFieldException e) {
                // 这里不用做处理，子类没有该字段可能对应的父类有，都没有就返回null。
            }
        }
        return field;
    }

    /**
     * 设置字段值
     * 
     * @param obj
     * @param fieldName
     * @param fieldValue
     */
    public static void setFieldValue(Object obj, String fieldName, Object fieldValue) {
        Field field = ReflectUtil.getField(obj, fieldName);
        field.setAccessible(true);
        String methodName = "set" + StringUtils.capitalize(fieldName);
        try {
            Method mt = obj.getClass().getMethod(methodName, field.getType());
            mt.invoke(obj, fieldValue);
        } catch (NoSuchMethodException nme) {
            try {
                field.set(obj, fieldValue);
            } catch (IllegalAccessException e) {
                logger.error("", e);
            }
        } catch (Exception e) {
            logger.error("", e);
        }
    }

    public static Method getMethod(String fullMethodName) throws Exception {
        if (StringUtils.isBlank(fullMethodName)) {
            return null;
        }
        int lastPointIndex = fullMethodName.lastIndexOf(".");
        String fullClazz = fullMethodName.substring(0, lastPointIndex);
        String methodName = fullMethodName.substring(lastPointIndex + 1, fullMethodName.length());
        return getMethod(fullClazz, methodName);
    }

    public static Method getMethod(String fullClazz, String methodName) throws Exception {
        if (StringUtils.isBlank(fullClazz) || StringUtils.isBlank(methodName)) {
            return null;
        }
        Class<?> clazz = Class.forName(fullClazz);
        return getMethod(clazz, methodName);
    }

    public static Method getMethod(Class<?> clazz, String methodName) throws Exception {
        if (clazz == null || StringUtils.isBlank(methodName)) {
            return null;
        }
        try {
            return clazz.getMethod(methodName);
        } catch (NoSuchMethodException ignored) {
        }
        Method[] ms = clazz.getMethods();
        for (Method m : ms) {
            if (m.getName().equals(methodName)) {
                return m;
            }
        }
        return null;
    }
}
