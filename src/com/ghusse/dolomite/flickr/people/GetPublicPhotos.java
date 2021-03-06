/**
 * Copyright (c) Guillaume Gautreau 2011
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author guillaumegautreau
 */

package com.ghusse.dolomite.flickr.people;

import com.ghusse.dolomite.flickr.Credentials;
import com.ghusse.dolomite.flickr.SafetyPhotoListRequest;

/**
 * Sends a request that will return a page of public photos of an user.
 * @author guillaumegautreau
 */
public class GetPublicPhotos extends SafetyPhotoListRequest {
  
  /**
   * Requested Flickr method.
   */
  public static final String METHOD = "flickr.people.getPublicPhotos";

  /**
   * Basic Ctor with only mandatory parameters.
   * @param credentials     Credentials for accessing to the API.
   * @param nsid            User NSID.
   */
  public GetPublicPhotos(final Credentials credentials, final String nsid) {
	super(METHOD, credentials);
	this.setArgument("user_id", nsid);
  }
}
