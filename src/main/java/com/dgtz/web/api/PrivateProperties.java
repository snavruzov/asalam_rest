package com.dgtz.web.api;

import com.brocast.riak.api.beans.DcUsersEntity;
import com.dgtz.api.contents.UsersShelf;

/**
 * Created by root on 1/15/14.
 */
public final class PrivateProperties {

    protected PrivateProperties() {

    }

    protected static Long extractUserRealIdent(String idHash, boolean check_hidden) {
        UsersShelf usersShelf = new UsersShelf();
        DcUsersEntity usersEntity = usersShelf.getUserInfoByHash(idHash);
        Long id_user = null;
        if (usersEntity != null) {
            if (check_hidden && usersEntity.getIdUser() != 0) {
                id_user = (false) ? 0 : usersEntity.getIdUser();
            } else if (!check_hidden) {
                id_user = usersEntity.getIdUser();
            } else {
                id_user = -1L;
            }
        }

        return id_user;
    }

    protected static DcUsersEntity extractUserFullInfo(String idHash) {
        UsersShelf usersShelf = new UsersShelf();
        DcUsersEntity usersEntity = usersShelf.getUserInfoByHash(idHash);

        return usersEntity;
    }

    protected static DcUsersEntity extractUserFullInfoById(Long idUser) {
        UsersShelf usersShelf = new UsersShelf();
        DcUsersEntity usersEntity = usersShelf.getUserInfoById(idUser);
        return usersEntity;
    }
}
