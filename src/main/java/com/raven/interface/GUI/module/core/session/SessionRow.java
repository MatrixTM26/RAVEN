package com.raven.interfaces.GUI.module.core.session;

import com.raven.core.session.Session;
import javafx.beans.property.SimpleStringProperty;

public class SessionRow {

    private final SimpleStringProperty Id, Type, Name, Ip, Os, User, Host, Joined;

    public SessionRow(Session S) {
        Id = new SimpleStringProperty(String.valueOf(S.GetId()));
        Type = new SimpleStringProperty(S.GetSessionType().name());
        Name = new SimpleStringProperty(S.GetDisplayName());
        Ip = new SimpleStringProperty(S.GetAgentIp());
        Os = new SimpleStringProperty(S.GetOs());
        User = new SimpleStringProperty(S.GetUser());
        Host = new SimpleStringProperty(S.GetHostname());
        Joined = new SimpleStringProperty(S.GetSessionKey());
    }

    public String getId() { return Id.get(); }
    public String getType() { return Type.get(); }
    public String getName() { return Name.get(); }
    public String getIp() { return Ip.get(); }
    public String getOs() { return Os.get(); }
    public String getUser() { return User.get(); }
    public String getHost() { return Host.get(); }
    public String getJoined() { return Joined.get(); }

    public SimpleStringProperty IdProperty() { return Id; }
    public SimpleStringProperty TypeProperty() { return Type; }
    public SimpleStringProperty NameProperty() { return Name; }
    public SimpleStringProperty IpProperty() { return Ip; }
    public SimpleStringProperty OsProperty() { return Os; }
    public SimpleStringProperty UserProperty() { return User; }
    public SimpleStringProperty HostProperty() { return Host; }
    public SimpleStringProperty JoinedProperty() { return Joined; }
}
