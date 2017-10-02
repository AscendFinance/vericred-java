/*
 * Vericred API
 * Vericred's API allows you to search for Health Plans that a specific doctor
accepts.

## Getting Started

Visit our [Developer Portal](https://developers.vericred.com) to
create an account.

Once you have created an account, you can create one Application for
Production and another for our Sandbox (select the appropriate Plan when
you create the Application).

## SDKs

Our API follows standard REST conventions, so you can use any HTTP client
to integrate with us. You will likely find it easier to use one of our
[autogenerated SDKs](https://github.com/vericred/?query=vericred-),
which we make available for several common programming languages.

## Authentication

To authenticate, pass the API Key you created in the Developer Portal as
a `Vericred-Api-Key` header.

`curl -H 'Vericred-Api-Key: YOUR_KEY' "https://api.vericred.com/providers?search_term=Foo&zip_code=11215"`

## Versioning

Vericred's API default to the latest version.  However, if you need a specific
version, you can request it with an `Accept-Version` header.

The current version is `v3`.  Previous versions are `v1` and `v2`.

`curl -H 'Vericred-Api-Key: YOUR_KEY' -H 'Accept-Version: v2' "https://api.vericred.com/providers?search_term=Foo&zip_code=11215"`

## Pagination

Endpoints that accept `page` and `per_page` parameters are paginated. They expose
four additional fields that contain data about your position in the response,
namely `Total`, `Per-Page`, `Link`, and `Page` as described in [RFC-5988](https://tools.ietf.org/html/rfc5988).

For example, to display 5 results per page and view the second page of a
`GET` to `/networks`, your final request would be `GET /networks?....page=2&per_page=5`.

## Sideloading

When we return multiple levels of an object graph (e.g. `Provider`s and their `State`s
we sideload the associated data.  In this example, we would provide an Array of
`State`s and a `state_id` for each provider.  This is done primarily to reduce the
payload size since many of the `Provider`s will share a `State`

```
{
  providers: [{ id: 1, state_id: 1}, { id: 2, state_id: 1 }],
  states: [{ id: 1, code: 'NY' }]
}
```

If you need the second level of the object graph, you can just match the
corresponding id.

## Selecting specific data

All endpoints allow you to specify which fields you would like to return.
This allows you to limit the response to contain only the data you need.

For example, let's take a request that returns the following JSON by default

```
{
  provider: {
    id: 1,
    name: 'John',
    phone: '1234567890',
    field_we_dont_care_about: 'value_we_dont_care_about'
  },
  states: [{
    id: 1,
    name: 'New York',
    code: 'NY',
    field_we_dont_care_about: 'value_we_dont_care_about'
  }]
}
```

To limit our results to only return the fields we care about, we specify the
`select` query string parameter for the corresponding fields in the JSON
document.

In this case, we want to select `name` and `phone` from the `provider` key,
so we would add the parameters `select=provider.name,provider.phone`.
We also want the `name` and `code` from the `states` key, so we would
add the parameters `select=states.name,states.code`.  The id field of
each document is always returned whether or not it is requested.

Our final request would be `GET /providers/12345?select=provider.name,provider.phone,states.name,states.code`

The response would be

```
{
  provider: {
    id: 1,
    name: 'John',
    phone: '1234567890'
  },
  states: [{
    id: 1,
    name: 'New York',
    code: 'NY'
  }]
}
```

## Benefits summary format
Benefit cost-share strings are formatted to capture:
 * Network tiers
 * Compound or conditional cost-share
 * Limits on the cost-share
 * Benefit-specific maximum out-of-pocket costs

**Example #1**
As an example, we would represent [this Summary of Benefits &amp; Coverage](https://s3.amazonaws.com/vericred-data/SBC/2017/33602TX0780032.pdf) as:

* **Hospital stay facility fees**:
  - Network Provider: `$400 copay/admit plus 20% coinsurance`
  - Out-of-Network Provider: `$1,500 copay/admit plus 50% coinsurance`
  - Vericred's format for this benefit: `In-Network: $400 before deductible then 20% after deductible / Out-of-Network: $1,500 before deductible then 50% after deductible`

* **Rehabilitation services:**
  - Network Provider: `20% coinsurance`
  - Out-of-Network Provider: `50% coinsurance`
  - Limitations & Exceptions: `35 visit maximum per benefit period combined with Chiropractic care.`
  - Vericred's format for this benefit: `In-Network: 20% after deductible / Out-of-Network: 50% after deductible | limit: 35 visit(s) per Benefit Period`

**Example #2**
In [this other Summary of Benefits &amp; Coverage](https://s3.amazonaws.com/vericred-data/SBC/2017/40733CA0110568.pdf), the **specialty_drugs** cost-share has a maximum out-of-pocket for in-network pharmacies.
* **Specialty drugs:**
  - Network Provider: `40% coinsurance up to a $500 maximum for up to a 30 day supply`
  - Out-of-Network Provider `Not covered`
  - Vericred's format for this benefit: `In-Network: 40% after deductible, up to $500 per script / Out-of-Network: 100%`

**BNF**

Here's a description of the benefits summary string, represented as a context-free grammar:

```
root                      ::= coverage

coverage                  ::= (simple_coverage | tiered_coverage) (space pipe space coverage_modifier)?
tiered_coverage           ::= tier (space slash space tier)*
tier                      ::= tier_name colon space (tier_coverage | not_applicable)
tier_coverage             ::= simple_coverage (space (then | or | and) space simple_coverage)* tier_limitation?
simple_coverage           ::= (pre_coverage_limitation space)? coverage_amount (space post_coverage_limitation)? (comma? space coverage_condition)?
coverage_modifier         ::= limit_condition colon space (((simple_coverage | simple_limitation) (semicolon space see_carrier_documentation)?) | see_carrier_documentation | waived_if_admitted | shared_across_tiers)
waived_if_admitted        ::= ("copay" space)? "waived if admitted"
simple_limitation         ::= pre_coverage_limitation space "copay applies"
tier_name                 ::= "In-Network-Tier-2" | "Out-of-Network" | "In-Network"
limit_condition           ::= "limit" | "condition"
tier_limitation           ::= comma space "up to" space (currency | (integer space time_unit plural?)) (space post_coverage_limitation)?
coverage_amount           ::= currency | unlimited | included | unknown | percentage | (digits space (treatment_unit | time_unit) plural?)
pre_coverage_limitation   ::= first space digits space time_unit plural?
post_coverage_limitation  ::= (((then space currency) | "per condition") space)? "per" space (treatment_unit | (integer space time_unit) | time_unit) plural?
coverage_condition        ::= ("before deductible" | "after deductible" | "penalty" | allowance | "in-state" | "out-of-state") (space allowance)?
allowance                 ::= upto_allowance | after_allowance
upto_allowance            ::= "up to" space (currency space)? "allowance"
after_allowance           ::= "after" space (currency space)? "allowance"
see_carrier_documentation ::= "see carrier documentation for more information"
shared_across_tiers       ::= "shared across all tiers"
unknown                   ::= "unknown"
unlimited                 ::= /[uU]nlimited/
included                  ::= /[iI]ncluded in [mM]edical/
time_unit                 ::= /[hH]our/ | (((/[cC]alendar/ | /[cC]ontract/) space)? /[yY]ear/) | /[mM]onth/ | /[dD]ay/ | /[wW]eek/ | /[vV]isit/ | /[lL]ifetime/ | ((((/[bB]enefit/ plural?) | /[eE]ligibility/) space)? /[pP]eriod/)
treatment_unit            ::= /[pP]erson/ | /[gG]roup/ | /[cC]ondition/ | /[sS]cript/ | /[vV]isit/ | /[eE]xam/ | /[iI]tem/ | /[sS]tay/ | /[tT]reatment/ | /[aA]dmission/ | /[eE]pisode/
comma                     ::= ","
colon                     ::= ":"
semicolon                 ::= ";"
pipe                      ::= "|"
slash                     ::= "/"
plural                    ::= "(s)" | "s"
then                      ::= "then" | ("," space) | space
or                        ::= "or"
and                       ::= "and"
not_applicable            ::= "Not Applicable" | "N/A" | "NA"
first                     ::= "first"
currency                  ::= "$" number
percentage                ::= number "%"
number                    ::= float | integer
float                     ::= digits "." digits
integer                   ::= /[0-9]/+ (comma_int | under_int)*
comma_int                 ::= ("," /[0-9]/*3) !"_"
under_int                 ::= ("_" /[0-9]/*3) !","
digits                    ::= /[0-9]/+ ("_" /[0-9]/+)*
space                     ::= /[ \t]/+
```


 *
 * OpenAPI spec version: 1.0.0
 * 
 *
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package model;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import java.io.Serializable;
/**
 * Provider
 */
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2017-10-02T17:06:11.296-04:00")
public class Provider  implements Serializable {
  @JsonProperty("city")
  private String city = null;

  @JsonProperty("email")
  private String email = null;

  @JsonProperty("gender")
  private String gender = null;

  @JsonProperty("first_name")
  private String firstName = null;

  @JsonProperty("id")
  private Integer id = null;

  @JsonProperty("last_name")
  private String lastName = null;

  @JsonProperty("latitude")
  private BigDecimal latitude = null;

  @JsonProperty("longitude")
  private BigDecimal longitude = null;

  @JsonProperty("middle_name")
  private String middleName = null;

  @JsonProperty("network_ids")
  private List<Integer> networkIds = new ArrayList<Integer>();

  @JsonProperty("organization_name")
  private String organizationName = null;

  @JsonProperty("personal_phone")
  private String personalPhone = null;

  @JsonProperty("phone")
  private String phone = null;

  @JsonProperty("presentation_name")
  private String presentationName = null;

  @JsonProperty("specialty")
  private String specialty = null;

  @JsonProperty("state")
  private String state = null;

  @JsonProperty("state_id")
  private Integer stateId = null;

  @JsonProperty("street_line_1")
  private String streetLine1 = null;

  @JsonProperty("street_line_2")
  private String streetLine2 = null;

  @JsonProperty("suffix")
  private String suffix = null;

  @JsonProperty("title")
  private String title = null;

  @JsonProperty("type")
  private String type = null;

  @JsonProperty("zip_code")
  private String zipCode = null;

  @JsonProperty("npis")
  private List<Integer> npis = new ArrayList<Integer>();

  public Provider city(String city) {
    this.city = city;
    return this;
  }

   /**
   * City name (e.g. Springfield).
   * @return city
  **/
  @ApiModelProperty(example = "null", value = "City name (e.g. Springfield).")
  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public Provider email(String email) {
    this.email = email;
    return this;
  }

   /**
   * Primary email address to contact the provider.
   * @return email
  **/
  @ApiModelProperty(example = "null", value = "Primary email address to contact the provider.")
  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public Provider gender(String gender) {
    this.gender = gender;
    return this;
  }

   /**
   * Provider's gender (M or F)
   * @return gender
  **/
  @ApiModelProperty(example = "null", value = "Provider's gender (M or F)")
  public String getGender() {
    return gender;
  }

  public void setGender(String gender) {
    this.gender = gender;
  }

  public Provider firstName(String firstName) {
    this.firstName = firstName;
    return this;
  }

   /**
   * Given name for the provider.
   * @return firstName
  **/
  @ApiModelProperty(example = "null", value = "Given name for the provider.")
  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public Provider id(Integer id) {
    this.id = id;
    return this;
  }

   /**
   * National Provider Index (NPI) number
   * @return id
  **/
  @ApiModelProperty(example = "null", value = "National Provider Index (NPI) number")
  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public Provider lastName(String lastName) {
    this.lastName = lastName;
    return this;
  }

   /**
   * Family name for the provider.
   * @return lastName
  **/
  @ApiModelProperty(example = "null", value = "Family name for the provider.")
  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public Provider latitude(BigDecimal latitude) {
    this.latitude = latitude;
    return this;
  }

   /**
   * Latitude of provider
   * @return latitude
  **/
  @ApiModelProperty(example = "null", value = "Latitude of provider")
  public BigDecimal getLatitude() {
    return latitude;
  }

  public void setLatitude(BigDecimal latitude) {
    this.latitude = latitude;
  }

  public Provider longitude(BigDecimal longitude) {
    this.longitude = longitude;
    return this;
  }

   /**
   * Longitude of provider
   * @return longitude
  **/
  @ApiModelProperty(example = "null", value = "Longitude of provider")
  public BigDecimal getLongitude() {
    return longitude;
  }

  public void setLongitude(BigDecimal longitude) {
    this.longitude = longitude;
  }

  public Provider middleName(String middleName) {
    this.middleName = middleName;
    return this;
  }

   /**
   * Middle name for the provider.
   * @return middleName
  **/
  @ApiModelProperty(example = "null", value = "Middle name for the provider.")
  public String getMiddleName() {
    return middleName;
  }

  public void setMiddleName(String middleName) {
    this.middleName = middleName;
  }

  public Provider networkIds(List<Integer> networkIds) {
    this.networkIds = networkIds;
    return this;
  }

  public Provider addNetworkIdsItem(Integer networkIdsItem) {
    this.networkIds.add(networkIdsItem);
    return this;
  }

   /**
   * Array of network ids
   * @return networkIds
  **/
  @ApiModelProperty(example = "null", value = "Array of network ids")
  public List<Integer> getNetworkIds() {
    return networkIds;
  }

  public void setNetworkIds(List<Integer> networkIds) {
    this.networkIds = networkIds;
  }

  public Provider organizationName(String organizationName) {
    this.organizationName = organizationName;
    return this;
  }

   /**
   * name for the providers of type: organization.
   * @return organizationName
  **/
  @ApiModelProperty(example = "null", value = "name for the providers of type: organization.")
  public String getOrganizationName() {
    return organizationName;
  }

  public void setOrganizationName(String organizationName) {
    this.organizationName = organizationName;
  }

  public Provider personalPhone(String personalPhone) {
    this.personalPhone = personalPhone;
    return this;
  }

   /**
   * Personal contact phone for the provider.
   * @return personalPhone
  **/
  @ApiModelProperty(example = "null", value = "Personal contact phone for the provider.")
  public String getPersonalPhone() {
    return personalPhone;
  }

  public void setPersonalPhone(String personalPhone) {
    this.personalPhone = personalPhone;
  }

  public Provider phone(String phone) {
    this.phone = phone;
    return this;
  }

   /**
   * Office phone for the provider
   * @return phone
  **/
  @ApiModelProperty(example = "null", value = "Office phone for the provider")
  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public Provider presentationName(String presentationName) {
    this.presentationName = presentationName;
    return this;
  }

   /**
   * Preferred name for display (e.g. Dr. Francis White may prefer Dr. Frank White)
   * @return presentationName
  **/
  @ApiModelProperty(example = "null", value = "Preferred name for display (e.g. Dr. Francis White may prefer Dr. Frank White)")
  public String getPresentationName() {
    return presentationName;
  }

  public void setPresentationName(String presentationName) {
    this.presentationName = presentationName;
  }

  public Provider specialty(String specialty) {
    this.specialty = specialty;
    return this;
  }

   /**
   * Name of the primary Specialty
   * @return specialty
  **/
  @ApiModelProperty(example = "null", value = "Name of the primary Specialty")
  public String getSpecialty() {
    return specialty;
  }

  public void setSpecialty(String specialty) {
    this.specialty = specialty;
  }

  public Provider state(String state) {
    this.state = state;
    return this;
  }

   /**
   * State code for the provider's address (e.g. NY).
   * @return state
  **/
  @ApiModelProperty(example = "null", value = "State code for the provider's address (e.g. NY).")
  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public Provider stateId(Integer stateId) {
    this.stateId = stateId;
    return this;
  }

   /**
   * Foreign key to States
   * @return stateId
  **/
  @ApiModelProperty(example = "null", value = "Foreign key to States")
  public Integer getStateId() {
    return stateId;
  }

  public void setStateId(Integer stateId) {
    this.stateId = stateId;
  }

  public Provider streetLine1(String streetLine1) {
    this.streetLine1 = streetLine1;
    return this;
  }

   /**
   * First line of the provider's street address.
   * @return streetLine1
  **/
  @ApiModelProperty(example = "null", value = "First line of the provider's street address.")
  public String getStreetLine1() {
    return streetLine1;
  }

  public void setStreetLine1(String streetLine1) {
    this.streetLine1 = streetLine1;
  }

  public Provider streetLine2(String streetLine2) {
    this.streetLine2 = streetLine2;
    return this;
  }

   /**
   * Second line of the provider's street address.
   * @return streetLine2
  **/
  @ApiModelProperty(example = "null", value = "Second line of the provider's street address.")
  public String getStreetLine2() {
    return streetLine2;
  }

  public void setStreetLine2(String streetLine2) {
    this.streetLine2 = streetLine2;
  }

  public Provider suffix(String suffix) {
    this.suffix = suffix;
    return this;
  }

   /**
   * Suffix for the provider's name (e.g. Jr)
   * @return suffix
  **/
  @ApiModelProperty(example = "null", value = "Suffix for the provider's name (e.g. Jr)")
  public String getSuffix() {
    return suffix;
  }

  public void setSuffix(String suffix) {
    this.suffix = suffix;
  }

  public Provider title(String title) {
    this.title = title;
    return this;
  }

   /**
   * Professional title for the provider (e.g. Dr).
   * @return title
  **/
  @ApiModelProperty(example = "null", value = "Professional title for the provider (e.g. Dr).")
  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public Provider type(String type) {
    this.type = type;
    return this;
  }

   /**
   * Type of NPI number (individual provider vs organization).
   * @return type
  **/
  @ApiModelProperty(example = "null", value = "Type of NPI number (individual provider vs organization).")
  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Provider zipCode(String zipCode) {
    this.zipCode = zipCode;
    return this;
  }

   /**
   * Postal code for the provider's address (e.g. 11215)
   * @return zipCode
  **/
  @ApiModelProperty(example = "null", value = "Postal code for the provider's address (e.g. 11215)")
  public String getZipCode() {
    return zipCode;
  }

  public void setZipCode(String zipCode) {
    this.zipCode = zipCode;
  }

  public Provider npis(List<Integer> npis) {
    this.npis = npis;
    return this;
  }

  public Provider addNpisItem(Integer npisItem) {
    this.npis.add(npisItem);
    return this;
  }

   /**
   * The National Provider Index (NPI) numbers associated with this provider.
   * @return npis
  **/
  @ApiModelProperty(example = "null", value = "The National Provider Index (NPI) numbers associated with this provider.")
  public List<Integer> getNpis() {
    return npis;
  }

  public void setNpis(List<Integer> npis) {
    this.npis = npis;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Provider provider = (Provider) o;
    return Objects.equals(this.city, provider.city) &&
        Objects.equals(this.email, provider.email) &&
        Objects.equals(this.gender, provider.gender) &&
        Objects.equals(this.firstName, provider.firstName) &&
        Objects.equals(this.id, provider.id) &&
        Objects.equals(this.lastName, provider.lastName) &&
        Objects.equals(this.latitude, provider.latitude) &&
        Objects.equals(this.longitude, provider.longitude) &&
        Objects.equals(this.middleName, provider.middleName) &&
        Objects.equals(this.networkIds, provider.networkIds) &&
        Objects.equals(this.organizationName, provider.organizationName) &&
        Objects.equals(this.personalPhone, provider.personalPhone) &&
        Objects.equals(this.phone, provider.phone) &&
        Objects.equals(this.presentationName, provider.presentationName) &&
        Objects.equals(this.specialty, provider.specialty) &&
        Objects.equals(this.state, provider.state) &&
        Objects.equals(this.stateId, provider.stateId) &&
        Objects.equals(this.streetLine1, provider.streetLine1) &&
        Objects.equals(this.streetLine2, provider.streetLine2) &&
        Objects.equals(this.suffix, provider.suffix) &&
        Objects.equals(this.title, provider.title) &&
        Objects.equals(this.type, provider.type) &&
        Objects.equals(this.zipCode, provider.zipCode) &&
        Objects.equals(this.npis, provider.npis);
  }

  @Override
  public int hashCode() {
    return Objects.hash(city, email, gender, firstName, id, lastName, latitude, longitude, middleName, networkIds, organizationName, personalPhone, phone, presentationName, specialty, state, stateId, streetLine1, streetLine2, suffix, title, type, zipCode, npis);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Provider {\n");
    
    sb.append("    city: ").append(toIndentedString(city)).append("\n");
    sb.append("    email: ").append(toIndentedString(email)).append("\n");
    sb.append("    gender: ").append(toIndentedString(gender)).append("\n");
    sb.append("    firstName: ").append(toIndentedString(firstName)).append("\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    lastName: ").append(toIndentedString(lastName)).append("\n");
    sb.append("    latitude: ").append(toIndentedString(latitude)).append("\n");
    sb.append("    longitude: ").append(toIndentedString(longitude)).append("\n");
    sb.append("    middleName: ").append(toIndentedString(middleName)).append("\n");
    sb.append("    networkIds: ").append(toIndentedString(networkIds)).append("\n");
    sb.append("    organizationName: ").append(toIndentedString(organizationName)).append("\n");
    sb.append("    personalPhone: ").append(toIndentedString(personalPhone)).append("\n");
    sb.append("    phone: ").append(toIndentedString(phone)).append("\n");
    sb.append("    presentationName: ").append(toIndentedString(presentationName)).append("\n");
    sb.append("    specialty: ").append(toIndentedString(specialty)).append("\n");
    sb.append("    state: ").append(toIndentedString(state)).append("\n");
    sb.append("    stateId: ").append(toIndentedString(stateId)).append("\n");
    sb.append("    streetLine1: ").append(toIndentedString(streetLine1)).append("\n");
    sb.append("    streetLine2: ").append(toIndentedString(streetLine2)).append("\n");
    sb.append("    suffix: ").append(toIndentedString(suffix)).append("\n");
    sb.append("    title: ").append(toIndentedString(title)).append("\n");
    sb.append("    type: ").append(toIndentedString(type)).append("\n");
    sb.append("    zipCode: ").append(toIndentedString(zipCode)).append("\n");
    sb.append("    npis: ").append(toIndentedString(npis)).append("\n");
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

