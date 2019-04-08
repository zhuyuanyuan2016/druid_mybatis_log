package mybatis.log.util;

import mybatis.log.ConfigVo;
import mybatis.log.hibernate.BasicFormatterImpl;
import org.apache.commons.lang.StringUtils;
import mybatis.log.hibernate.Formatter;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * restore the mybatis generate sql to original whole sql
 * @author ob
 */
public class RestoreSqlUtil {
    private static Set<String> needAssembledType = new HashSet<>();
    private static final String QUESTION_MARK = "?";
    private static final String REPLACE_MARK = "_o_?_b_";
    private static final String PARAM_TYPE_REGEX = "\\(\\D{3,30}?\\),{0,1}";

    private static String prevLine = "";
    //参数格式类型，暂列下面几种
    static {
        needAssembledType.add("(String)");
        needAssembledType.add("(Timestamp)");
        needAssembledType.add("(Date)");
        needAssembledType.add("(Time)");
    }

    public static String match(String p, String str) {
        Pattern pattern = Pattern.compile(p);
        Matcher m = pattern.matcher(str);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    /**
     * Sql语句还原，整个插件的核心就是该方法
     * @param preparing
     * @param parameters
     * @return
     */
    public static String restoreSql(final String preparing, final String parameters) {
        String restoreSql = "";
        String preparingSql = "";
        String parametersSql = "";
        try {

            if(preparing.contains(StringConst.PREPARING)) {
                preparingSql = preparing.split(StringConst.PREPARING)[1].trim();
            }
            // mybatis plus
            else if (preparing.contains(StringConst.CREATEDS)) {
                preparingSql = preparing.split(StringConst.CREATEDS)[1].trim();
            } else {
                preparingSql = preparing;
            }
            boolean hasParam = false;
            if(parameters.contains(StringConst.PARAMETERS)) {
                if(parameters.split(StringConst.PARAMETERS).length > 1) {
                    parametersSql = parameters.split(StringConst.PARAMETERS)[1];
                    if(StringUtils.isNotBlank(parametersSql)) {
                        hasParam = true;
                    }
                }
            } else if (parameters.contains(StringConst.PARAMETERS2)) {
                if(parameters.split(StringConst.PARAMETERS2).length > 1) {
                    parametersSql = parameters.split(StringConst.PARAMETERS2)[1];
                    if(StringUtils.isNotBlank(parametersSql)) {
                        hasParam = true;
                    }
                }
            } else {
                parametersSql = parameters;
            }
            if(hasParam) {
                preparingSql = StringUtils.replace(preparingSql, QUESTION_MARK, REPLACE_MARK);
                preparingSql = StringUtils.removeEnd(preparingSql, "\n");
                if (preparing.contains(StringConst.CREATEDS)) {
                    if (preparing.contains("LIMIT") || preparing.contains("limit") ) {
                        parametersSql = parametersSql.replace("[", "").replace("]", "");
                    }else{
                      parametersSql = parametersSql.replace("[", "'").replace("]", "'");
                    }
                }
                parametersSql = StringUtils.removeEnd(parametersSql, "\n");
                int questionMarkCount = StringUtils.countMatches(preparingSql, REPLACE_MARK);
                String[] paramArray = parametersSql.split(PARAM_TYPE_REGEX);
                boolean matches = !(parametersSql.lastIndexOf(")") >-1);
                if (matches) {

                    paramArray = parametersSql.split(",");
                }
                for(int i=0; i<paramArray.length; ++i) {
                    if(questionMarkCount <= paramArray.length || parametersSql.indexOf("null") == -1) {
                        break;
                    } else {
                        parametersSql = parametersSql.replaceFirst("null,", "null(Null),");//这个一定要用null,(带逗号)，否则多个null值分割会出错
                    }
                    paramArray = parametersSql.split(PARAM_TYPE_REGEX);
                }

                if (matches) {
                    System.out.println(parametersSql);
                    for(int i=0; i<paramArray.length; ++i) {
                        paramArray[i] = StringUtils.removeStart(paramArray[i], " ").replace("[","").replace("]","");
                        parametersSql = StringUtils.replaceOnce(StringUtils.removeStart(parametersSql, " "), paramArray[i], "");
                        String  paramType = "(String)"; //
                        if (StringUtils.isNotBlank(paramArray[i])) {
                            String temp = paramArray[i].replace("[","").replace("]","");
                            boolean matches1 = temp.matches("\\d+");
                            if (matches1) {
                                paramType = "(Integer)";
                            }
                        }
                        preparingSql = StringUtils.replaceOnce(preparingSql, REPLACE_MARK, assembledParamValue(paramArray[i], paramType));
                        paramType = paramType.replace("(", "\\(").replace(")", "\\)") + ", ";
                        parametersSql = parametersSql.replaceFirst(paramType, "");
                    }
                }else{
                    for(int i=0; i<paramArray.length; ++i) {
                        paramArray[i] = StringUtils.removeStart(paramArray[i], " ");
                        parametersSql = StringUtils.replaceOnce(StringUtils.removeStart(parametersSql, " "), paramArray[i], "");
                        String paramType = match("(\\(\\D{3,25}?\\))", parametersSql);
                        preparingSql = StringUtils.replaceOnce(preparingSql, REPLACE_MARK, assembledParamValue(paramArray[i], paramType));
                        paramType = paramType.replace("(", "\\(").replace(")", "\\)") + ", ";
                        parametersSql = parametersSql.replaceFirst(paramType, "");
                    }

                }
            }
            restoreSql = simpleFormat(preparingSql);
            if(!restoreSql.endsWith(";")) {
                restoreSql += ";";
            }
            if(restoreSql.contains(REPLACE_MARK)) {
                restoreSql = StringUtils.replace(restoreSql, REPLACE_MARK, "error");
                restoreSql += "\n---This is an error sql!---";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "restore mybatis sql error!";
        }
        return restoreSql;
    }

    public static String assembledParamValue(String paramValue, String paramType) {
        if(needAssembledType.contains(paramType)) {
            paramValue = "'" + paramValue + "'";
        }
        return paramValue;
    }

    /**
     * 简单的格式化
     * @param sql
     * @return
     */
    public static String simpleFormat(String sql) {
        if(StringUtils.isNotBlank(sql)) {
            return sql.replaceAll("(?i)\\s+from\\s+", "\n FROM ")
                    .replaceAll("(?i)\\s+select\\s+", "\n SELECT ")
                    .replaceAll("(?i)\\s+where\\s+", "\n WHERE ")
                    .replaceAll("(?i)\\s+left join\\s+", "\n LEFT JOIN ")
                    .replaceAll("(?i)\\s+right join\\s+", "\n RIGHT JOIN ")
                    .replaceAll("(?i)\\s+inner join\\s+", "\n INNER JOIN ")
                    .replaceAll("(?i)\\s+limit\\s+", "\n LIMIT ")
                    .replaceAll("(?i)\\s+on\\s+", "\n ON ")
                    .replaceAll("(?i)\\s+union\\s+", "\n UNION ");
        }
        return "";
    }

    public static void main1(String[] args) {
        String sql = "2017-06-23 14:31:27.729 ERROR notParamTest - ==>  Preparing: INSERT INTO t_ml_vop_bil_interface (a,b,c) VALUES (?,?,?)\n";
        String param = "2017-06-23 14:31:27.729 ERROR notParamTest - ==>  Parameters: 996aep(String), {succ,?,ess=1}(String), 2017-06-29(Timestamp)\n";
        String restoreSql = restoreSql(sql, param);
        Formatter formatter = new BasicFormatterImpl();
        String result = formatter.format(restoreSql);
        System.out.println(restoreSql);
        System.out.println("----------------------");
        System.out.println(result);

    }


    public static void main(String[] args) {
        String sql = "2019-04-08 01:23:30.765 [main] DEBUG [com.alibaba.druid.filter.logging.Log4j2Filter.statementLog] - {conn-110001, pstmt-120000} created. insert into t_user (user_id, user_name, password,\n" +
                "      phone)\n" +
                "    values (?, ?, ?,\n" +
                "      ?)";
        String param = "2019-04-08 01:23:30.780 [main] DEBUG [com.alibaba.druid.filter.logging.Log4j2Filter.statementLog] - {conn-110001, pstmt-120000} Parameters : [10, username, password, phone]";
        String restoreSql = restoreSql(sql, param);
        System.out.println("restoreSql" + restoreSql);
        Formatter formatter = new BasicFormatterImpl();
        String result = formatter.format(restoreSql);
        System.out.println(restoreSql);
        System.out.println("----------------------");
        System.out.println(result);
        ConfigVo configVo = new ConfigVo();
        configVo.setSqlFormat(true);
        configVo.setIndexNum(0);

        System.out.println("=====================================================================");


    }





}