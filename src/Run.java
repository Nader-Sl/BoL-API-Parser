
/**
 * @author Nader Sl 
 * PS: MAKE SURE TO HAVE THE DOXYGEN.HTML FILES IN THE
 * prototypesDir AND A FRESH COPY OF THE ORIGINAL GLOBAL.LUA (FOUND IN ROOT) IN
 * THE docsDir BEFORE EVERY RUN. An Custom Eclipse Lua Development Toolkit
 * module Execution Environment generator exclusive for the BoL API based on the
 * up-to-date doxygen's BoL API's docs
 *
 * @ (http://botoflegends.com/doxygen/)
 */
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Run {

    final public static String docsDir = "BoL API Docs";//dir containing the BoL API docs to parse (in doxygen docs format).
    final public static String prototypesDir = "BoLPrototypes";//dir to hold the BoL generated prototypes/modules.
    final static List<String> primitives = Arrays.asList("int", "float", "double", "DWORD", "bool", "short", "long", "byte", "char");

    public static void main(String[] argV) throws Exception {
        try {

            final File classesList = new File(docsDir + "/annotated.html");
            final Document doc = Jsoup.parse(classesList, "UTF-8", "/");
            final StringBuilder globalContent = new StringBuilder();
            final Elements entries = doc.select(".entry");
            for (Element cEntry : entries) {
                final Element cImg = cEntry.child(1);
                final Element cAnchor = cEntry.child(2);
                final Element cDesc = cEntry.siblingElements().get(0);
                final String luaClassName = cAnchor.text();
                final boolean isClass = cImg.attr("alt").equals("C");
                String cHref = cAnchor.attr("href");
                cHref = cHref.substring(cHref.lastIndexOf("/") + 1);
                //   System.out.println(cHref + " (" + cImg.attr("alt") + ") " + luaClassName + " " + cDesc.text());
                File currClass = new File(docsDir + "/" + cHref);
                StringBuilder classContent = new StringBuilder(), currentContent = null;
                classContent.append("\n" + "-------------------------------------------------------------------------------" + "\n");
                classContent.append("-- ").append(cDesc.text()).append("\n");
                classContent.append("-- @type ").append(luaClassName).append("\n");
                classContent.append("-- @extends ").append("#").append("\n");

                try {
                    Elements methods = null;
                    Document currDoc = Jsoup.parse(currClass, "UTF-8", "/");
                    try {
                        methods = currDoc.select(isClass ? "a[name=pub-methods]" : "a[name=func-members]").parents().get(2).siblingElements().select("tr[class^=memitem]");
                    } catch (IndexOutOfBoundsException iob) {
                        //ingnore.
                    }
                    if (methods != null) {
                        for (Element method : methods) {
                            String mType = method.child(0).text();
                            mType = getLuaType(luaClassName, mType.substring(0, mType.length() - 1));
                            final String mName = method.child(1).child(0).text();
                            final String mDesc = currDoc.select("a[id=" + method.attr("class").split(":")[1] + "]").get(0).nextElementSibling().child(1).text();

                            currentContent = isClass ? classContent : globalContent;
                            currentContent.append("\n" + "\n" + "-------------------------------------------------------------------------------" + "\n");
                            currentContent.append("-- ").append(mDesc).append("\n");
                            currentContent.append("-- @function [parent=#").append(isClass ? luaClassName : "global").append("]" + " ").append(mName).append("\n");
                            //parse params
                            String mParam = method.child(1).text().replaceAll("&", "").replaceAll("\\*", "");
                            System.out.println(mParam);
                            mParam = mParam.substring(mParam.indexOf("(") + 1, mParam.length() - 1);
                            if (isClass) {
                                classContent.append("-- @param self" + "\n");//.append(luaClassName).append("\n");
                            }
                            if (mParam.length() > 0) {
                                String[] params = mParam.split(",");
                                for (String param : params) {
                                    String[] paramData = param.trim().split(" ");
                                    String paramType = getLuaType(luaClassName, paramData[paramData.length - 2]);
                                    String paramName = paramData[paramData.length - 1];
                                    currentContent.append("-- @param ").append(paramType).append(" ").append(paramName).append("\n");

                                }
                            }

                            if (!mType.startsWith("void")) {
                                boolean isConstructor = mType.length() == 1;
                                currentContent.append("-- @return ").append(mType).append(isConstructor ? luaClassName : "").append("\n");
                            }
                        }
                    }
                    Elements fields = null;
                    try {
                        fields = currDoc.select("a[name=pub-attribs]").parents().get(2).siblingElements().select("tr[class^=memitem]");
                    } catch (IndexOutOfBoundsException iob) {
                        //ignore.
                    }
                    if (fields != null) {
                        for (Element field : fields) {

                            final String fType = field.child(0).text();
                            final String fName = field.child(1).child(0).text();
                            final String fDesc = currDoc.select("a[id=" + field.attr("class").split(":")[1] + "]").get(0).nextElementSibling().child(1).text();
                            if (isClass) {
                                currentContent = classContent;
                            }
                            currentContent.append("\n" + "\n" + "-------------------------------------------------------------------------------" + "\n");
                            currentContent.append("-- ").append(fDesc).append("\n");
                            currentContent.append("-- @field [parent=#").append(isClass ? luaClassName : "global").append("] ").append(getLuaType(luaClassName, fType.substring(0, fType.length() - 1))).append(" ").append(fName).append("\n");
                        }
                    }
                    //add instance to global content
                    if (isClass) {
                        globalContent.append("\n" + "\n" + "-------------------------------------------------------------------------------" + "\n");
                        globalContent.append("-- ").append(luaClassName).append(" preloaded module").append("\n");
                        globalContent.append("-- @field[parent=#global] ").append(luaClassName).append("#").append(luaClassName).append(" ").append(luaClassName).append(" preloaded module");
                        classContent.append("\n\n" + "return nil");
                    }
                    if (isClass) {
                        saveLuaFile(luaClassName, classContent.toString());
                    }

                } catch (FileNotFoundException fnf) {
                    continue;
                }
            }
            extendGlobalFile(globalContent.toString() + "\n\n" + "return nil");
            System.out.println("appended the core functions and fields to the global file global.lua");
        } catch (Exception ioe) {
            ioe.printStackTrace();
        }
    }

    public static String getLuaType(String luaClassName, String type) {
        type = type.trim();
        return (type.equals(luaClassName) || primitives.contains(type) ? "" : type) + "#" + type;
    }

    public static void saveLuaFile(final String name, final String content) throws IOException {
        final File file = new File(prototypesDir + "/" + name + ".lua");
        if (!file.exists()) {
            file.createNewFile();
        }
        final FileWriter fw = new FileWriter(file.getAbsoluteFile());
        final BufferedWriter bw = new BufferedWriter(fw);
        bw.write(content);
        bw.close();
    }

    public static void extendGlobalFile(final String content) throws FileNotFoundException, IOException {
        final RandomAccessFile f = new RandomAccessFile("BoLPrototypes/global.lua", "rw");
        long length = f.length() - 1;
        byte b;
        do {
            length -= 1;
            f.seek(length);
            b = f.readByte();
        } while (b != 10);
        f.setLength(length + 1);
        f.seek(length);
        f.writeBytes(content);
    }
}