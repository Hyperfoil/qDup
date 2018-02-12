package perf.ssh;

import org.junit.Test;

import java.util.Map;

public class EnvTest {


    @Test
    public void parse(){
        String input = "QTINC=/usr/lib64/qt-3.3/include\n" +
                "LD_LIBRARY_PATH=/opt/ide/idea-IC-172.3317.76/bin:\n" +
                "XDG_MENU_PREFIX=gnome-\n" +
                "LANG=en_US.UTF-8\n" +
                "GDM_LANG=en_US.UTF-8\n" +
                "HISTCONTROL=ignoredups\n" +
                "DISPLAY=:0\n" +
                "HOSTNAME=laptop\n" +
                "QTDIR=/usr/lib64/qt-3.3\n" +
                "OLDPWD=/opt/ide/idea-IC-172.3317.76/bin\n" +
                "MAVEN_HOME=/home/wreicher/tool/maven/current\n" +
                "XFILESEARCHPATH=/usr/dt/app-defaults/%L/Dt\n" +
                "NODE_HOME=/home/wreicher/tool/node/current\n" +
                "PGINSTALL=/usr/pgsql-9.5\n" +
                "USERNAME=wreicher\n" +
                "JAVA_HOME=/etc/alternatives/java_sdk\n" +
                "VERTX_HOME=/home/wreicher/tool/vertx/current\n" +
                "XDG_VTNR=2\n" +
                "GIO_LAUNCHED_DESKTOP_FILE_PID=5132\n" +
                "SSH_AUTH_SOCK=/run/user/1000/keyring/ssh\n" +
                "OC_HOME=/home/wreicher/tool/minishift/current/cache/oc/v1.5.0\n" +
                "XDG_SESSION_ID=2\n" +
                "USER=wreicher\n" +
                "MINISHIFT_HOME=/home/wreicher/tool/minishift/current\n" +
                "DESKTOP_SESSION=gnome\n" +
                "WAYLAND_DISPLAY=wayland-0\n" +
                "GRADLE_HOME=/home/wreicher/tool/gradle/current\n" +
                "PWD=/home/wreicher/code/perf/qDup\n" +
                "LINES=67\n" +
                "HOME=/home/wreicher\n" +
                "JOURNAL_STREAM=9:34074\n" +
                "XDG_SESSION_TYPE=wayland\n" +
                "XDG_DATA_DIRS=/home/wreicher/.local/share/flatpak/exports/share/:/var/lib/flatpak/exports/share/:/usr/local/share/:/usr/share/\n" +
                "PGDATA=/var/lib/pgsql/9.5/data\n" +
                "NLSPATH=/usr/dt/lib/nls/msg/%L/%N.cat\n" +
                "XDG_SESSION_DESKTOP=gnome\n" +
                "GJS_DEBUG_OUTPUT=stderr\n" +
                "LOADEDMODULES=\n" +
                "NPM_HOME=/home/wreicher/.npm\n" +
                "COLUMNS=240\n" +
                "MAIL=/var/spool/mail/wreicher\n" +
                "QTLIB=/usr/lib64/qt-3.3/lib\n" +
                "TERM=vt100\n" +
                "SHELL=/bin/bash\n" +
                "QT_IM_MODULE=ibus\n" +
                "XMODIFIERS=@im=ibus\n" +
                "XDG_CURRENT_DESKTOP=GNOME\n" +
                "GIO_LAUNCHED_DESKTOP_FILE=/usr/local/share/applications/jetbrains-idea-ce.desktop\n" +
                "XDG_SEAT=seat0\n" +
                "SHLVL=3\n" +
                "MODULEPATH=/etc/scl/modulefiles:/etc/scl/modulefiles:/usr/share/Modules/modulefiles:/etc/modulefiles:/usr/share/modulefiles\n" +
                "GDMSESSION=gnome\n" +
                "GNOME_DESKTOP_SESSION_ID=this-is-deprecated\n" +
                "LOGNAME=wreicher\n" +
                "DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/1000/bus\n" +
                "XDG_RUNTIME_DIR=/run/user/1000\n" +
                "ANT_HOME=/home/wreicher/tool/ant/current\n" +
                "PATH=/home/wreicher/tool/vertx/current/bin:/home/wreicher/tool/ant/current/bin:/home/wreicher/tool/byteman/current/bin:/home/wreicher/tool/gradle/current/bin:/etc/alternatives/java_sdk/bin:/home/wreicher/tool/maven/current/bin:/home/wreicher/tool/node/current/bin:/home/wreicher/tool/minishift/current:/home/wreicher/tool/minishift/current/cache/oc/v1.5.0:/home/wreicher/tool/vertx/current/bin:/home/wreicher/tool/ant/current/bin:/home/wreicher/tool/byteman/current/bin:/home/wreicher/tool/gradle/current/bin:/etc/alternatives/java_sdk/bin:/home/wreicher/tool/maven/current/bin:/home/wreicher/tool/node/current/bin:/home/wreicher/tool/minishift/current:/home/wreicher/tool/minishift/current/cache/oc/v1.5.0:/usr/lib64/qt-3.3/bin:/usr/lib64/ccache:/usr/local/bin:/usr/local/sbin:/usr/bin:/usr/sbin:/home/wreicher/.local/bin:/home/wreicher/bin:/home/wreicher/.local/bin:/home/wreicher/bin\n" +
                "PS1=\n" +
                "MODULESHOME=/usr/share/Modules\n" +
                "HISTSIZE=1000\n" +
                "GJS_DEBUG_TOPICS=JS ERROR;JS LOG\n" +
                "SESSION_MANAGER=local/unix:@/tmp/.ICE-unix/1510,unix/unix:/tmp/.ICE-unix/1510\n" +
                "LESSOPEN=||/usr/bin/lesspipe.sh %s\n" +
                "BYTEMAN_HOME=/home/wreicher/tool/byteman/current\n" +
                "BASH_FUNC_module%%=() {  eval `/usr/bin/modulecmd bash $*`\n" +
                "}\n" +
                "BASH_FUNC_scl%%=() {  local CMD=$1;\n" +
                " if [ \"$CMD\" = \"load\" -o \"$CMD\" = \"unload\" ]; then\n" +
                " eval \"module $@\";\n" +
                " else\n" +
                " /usr/bin/scl \"$@\";\n" +
                " fi\n" +
                "}\n" +
                "_=/usr/bin/\n" +
                "\n";


        Map<String,String> map = Env.parse(input);

        map.forEach((k,v)->{
            System.out.println(k+"-->"+v);
        });
    }
}
