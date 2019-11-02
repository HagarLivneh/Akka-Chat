
# The Actor Model using Akka

This project is an implementation of a textual-based WhatsApp!
The application will rely on two main features:

1-to-1 chat. In this feature a person using the application will send an addressed message to a specific destination.
1-to-many chat. In this feature a person using the application will
send a message addressed to the group itself. This message is broadcast
to all members found in the specific group.


# Chat Features
a person may send a message containing either text, or binary date to an addressed target. The textual data will be displayed on screen. 
The binary data will be saved, and the full path of the file will be printed
on screen. The binary data may contain any form of data, such as image, video, executable, etc.

textual message: /user text <target> <message>
binary message: /user file <target> <sourcefilePath>


# User Features
A user may connect to the server and disconnct from the server. A user connecting to the server will do so by choosing their username. 
The username must be unused one, and is unique. The server will hold the details of online users only. 
A disconnected user equals to a deleted user.

Connecting a user to the server: /user connect <username>
Disconnecting a user from the server: /user disconnect


# Group Chat Features
Group chat has multiple commands and multiple features. A user creating a group will be its admin. 
The admin can invite users to group, remove users from group, and promote users found in group to be co-admins. 
Lastly the admin can demote co-admins back to users or even remove them from group. 
A co-admin removed from group will lose their co-admin previledges as well as the ability to post in the group. 
A user in group can be of one of 4 possible modes: (1) admin (2) co-admin (3) user (4) muted user. 
These modes are in relation to a specific group! A user may be an admin of group one, while being a muted user in group two.

# Previledges
group admin:

may send text/file messages
may add/remove co-admins
may invite/remove users
may promotes: muted user to user to co-admin
may demote: co-admin to user to muted user
may create new group
may leave existing group

group co-admin:

may send text/file messages
may invite/remove users
may promote: muted user to user
may demote: user to muted user
may create new group
may leave existing gorup

group user:

may send text/file messages
may create new group
may leave existing group

group muted user:

may create new group
may leave existing group

# Commands

create group: /group create <groupname>
leave group: /group leave <groupname>
send text to group: /group send text <groupname> <message>
send binary message: /group send file <groupname> <sourcefilePath>
invite user: /group user invite <groupname> <targetusername>
remove user: /group user remove <groupname> <targetusername>
mute user: /group user mute <groupname> <targetusername> <timeinseconds>
unmute user: /group user unmute <groupname> <targetusername>
promote to co-admin: /group coadmin add <groupname> <targetusername>
demote co-admin: /group coadmin remove <groupname> <targetusername>
