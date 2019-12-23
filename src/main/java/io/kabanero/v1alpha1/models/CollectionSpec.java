/*
 * Kubernetes
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 * The version of the OpenAPI document: v1.17.0
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */


package io.kabanero.v1alpha1.models;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import com.google.gson.annotations.SerializedName;
import io.kabanero.v1alpha1.models.CollectionSpecVersions;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * CollectionSpec
 */
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2019-12-23T16:26:35.229Z[Etc/UTC]")
public class CollectionSpec {
  public static final String SERIALIZED_NAME_DESIRED_STATE = "desiredState";
  @SerializedName(SERIALIZED_NAME_DESIRED_STATE)
  private String desiredState;

  public static final String SERIALIZED_NAME_NAME = "name";
  @SerializedName(SERIALIZED_NAME_NAME)
  private String name;

  public static final String SERIALIZED_NAME_REPOSITORY_URL = "repositoryUrl";
  @SerializedName(SERIALIZED_NAME_REPOSITORY_URL)
  private String repositoryUrl;

  public static final String SERIALIZED_NAME_VERSION = "version";
  @SerializedName(SERIALIZED_NAME_VERSION)
  private String version;

  public static final String SERIALIZED_NAME_VERSIONS = "versions";
  @SerializedName(SERIALIZED_NAME_VERSIONS)
  private List<CollectionSpecVersions> versions = null;


  public CollectionSpec desiredState(String desiredState) {
    
    this.desiredState = desiredState;
    return this;
  }

   /**
   * Get desiredState
   * @return desiredState
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public String getDesiredState() {
    return desiredState;
  }


  public void setDesiredState(String desiredState) {
    this.desiredState = desiredState;
  }


  public CollectionSpec name(String name) {
    
    this.name = name;
    return this;
  }

   /**
   * Get name
   * @return name
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public String getName() {
    return name;
  }


  public void setName(String name) {
    this.name = name;
  }


  public CollectionSpec repositoryUrl(String repositoryUrl) {
    
    this.repositoryUrl = repositoryUrl;
    return this;
  }

   /**
   * Get repositoryUrl
   * @return repositoryUrl
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public String getRepositoryUrl() {
    return repositoryUrl;
  }


  public void setRepositoryUrl(String repositoryUrl) {
    this.repositoryUrl = repositoryUrl;
  }


  public CollectionSpec version(String version) {
    
    this.version = version;
    return this;
  }

   /**
   * Get version
   * @return version
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public String getVersion() {
    return version;
  }


  public void setVersion(String version) {
    this.version = version;
  }


  public CollectionSpec versions(List<CollectionSpecVersions> versions) {
    
    this.versions = versions;
    return this;
  }

  public CollectionSpec addVersionsItem(CollectionSpecVersions versionsItem) {
    if (this.versions == null) {
      this.versions = new ArrayList<CollectionSpecVersions>();
    }
    this.versions.add(versionsItem);
    return this;
  }

   /**
   * Get versions
   * @return versions
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public List<CollectionSpecVersions> getVersions() {
    return versions;
  }


  public void setVersions(List<CollectionSpecVersions> versions) {
    this.versions = versions;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    return EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public int hashCode() {
    return HashCodeBuilder.reflectionHashCode(this);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class CollectionSpec {\n");
    sb.append("    desiredState: ").append(toIndentedString(desiredState)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    repositoryUrl: ").append(toIndentedString(repositoryUrl)).append("\n");
    sb.append("    version: ").append(toIndentedString(version)).append("\n");
    sb.append("    versions: ").append(toIndentedString(versions)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }

}

