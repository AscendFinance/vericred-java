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
import java.util.ArrayList;
import java.util.List;
import model.PlanIdentifier;

import java.io.Serializable;
/**
 * PlanMedicare
 */
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2017-10-02T17:06:11.296-04:00")
public class PlanMedicare  implements Serializable {
  @JsonProperty("carrier_name")
  private String carrierName = null;

  @JsonProperty("display_name")
  private String displayName = null;

  @JsonProperty("effective_date")
  private String effectiveDate = null;

  @JsonProperty("expiration_date")
  private String expirationDate = null;

  @JsonProperty("identifiers")
  private List<PlanIdentifier> identifiers = new ArrayList<PlanIdentifier>();

  @JsonProperty("name")
  private String name = null;

  @JsonProperty("network_ids")
  private List<Integer> networkIds = new ArrayList<Integer>();

  @JsonProperty("network_size")
  private Integer networkSize = null;

  @JsonProperty("plan_type")
  private String planType = null;

  @JsonProperty("service_area_id")
  private String serviceAreaId = null;

  @JsonProperty("source")
  private String source = null;

  @JsonProperty("id")
  private String id = null;

  public PlanMedicare carrierName(String carrierName) {
    this.carrierName = carrierName;
    return this;
  }

   /**
   * Name of the insurance carrier
   * @return carrierName
  **/
  @ApiModelProperty(example = "null", value = "Name of the insurance carrier")
  public String getCarrierName() {
    return carrierName;
  }

  public void setCarrierName(String carrierName) {
    this.carrierName = carrierName;
  }

  public PlanMedicare displayName(String displayName) {
    this.displayName = displayName;
    return this;
  }

   /**
   * Alternate name for the Plan
   * @return displayName
  **/
  @ApiModelProperty(example = "null", value = "Alternate name for the Plan")
  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public PlanMedicare effectiveDate(String effectiveDate) {
    this.effectiveDate = effectiveDate;
    return this;
  }

   /**
   * Effective date of coverage.
   * @return effectiveDate
  **/
  @ApiModelProperty(example = "null", value = "Effective date of coverage.")
  public String getEffectiveDate() {
    return effectiveDate;
  }

  public void setEffectiveDate(String effectiveDate) {
    this.effectiveDate = effectiveDate;
  }

  public PlanMedicare expirationDate(String expirationDate) {
    this.expirationDate = expirationDate;
    return this;
  }

   /**
   * Expiration date of coverage.
   * @return expirationDate
  **/
  @ApiModelProperty(example = "null", value = "Expiration date of coverage.")
  public String getExpirationDate() {
    return expirationDate;
  }

  public void setExpirationDate(String expirationDate) {
    this.expirationDate = expirationDate;
  }

  public PlanMedicare identifiers(List<PlanIdentifier> identifiers) {
    this.identifiers = identifiers;
    return this;
  }

  public PlanMedicare addIdentifiersItem(PlanIdentifier identifiersItem) {
    this.identifiers.add(identifiersItem);
    return this;
  }

   /**
   * List of identifiers of this Plan
   * @return identifiers
  **/
  @ApiModelProperty(example = "null", value = "List of identifiers of this Plan")
  public List<PlanIdentifier> getIdentifiers() {
    return identifiers;
  }

  public void setIdentifiers(List<PlanIdentifier> identifiers) {
    this.identifiers = identifiers;
  }

  public PlanMedicare name(String name) {
    this.name = name;
    return this;
  }

   /**
   * Marketing name of the plan
   * @return name
  **/
  @ApiModelProperty(example = "null", value = "Marketing name of the plan")
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public PlanMedicare networkIds(List<Integer> networkIds) {
    this.networkIds = networkIds;
    return this;
  }

  public PlanMedicare addNetworkIdsItem(Integer networkIdsItem) {
    this.networkIds.add(networkIdsItem);
    return this;
  }

   /**
   * List of Vericred-generated network_ids
   * @return networkIds
  **/
  @ApiModelProperty(example = "null", value = "List of Vericred-generated network_ids")
  public List<Integer> getNetworkIds() {
    return networkIds;
  }

  public void setNetworkIds(List<Integer> networkIds) {
    this.networkIds = networkIds;
  }

  public PlanMedicare networkSize(Integer networkSize) {
    this.networkSize = networkSize;
    return this;
  }

   /**
   * Total number of Providers in network
   * @return networkSize
  **/
  @ApiModelProperty(example = "null", value = "Total number of Providers in network")
  public Integer getNetworkSize() {
    return networkSize;
  }

  public void setNetworkSize(Integer networkSize) {
    this.networkSize = networkSize;
  }

  public PlanMedicare planType(String planType) {
    this.planType = planType;
    return this;
  }

   /**
   * Category of the plan (e.g. EPO, HMO, PPO, POS, Indemnity, PACE, Medicare-Medicaid, HMO w/POS, Cost, FFS, MSA)
   * @return planType
  **/
  @ApiModelProperty(example = "null", value = "Category of the plan (e.g. EPO, HMO, PPO, POS, Indemnity, PACE, Medicare-Medicaid, HMO w/POS, Cost, FFS, MSA)")
  public String getPlanType() {
    return planType;
  }

  public void setPlanType(String planType) {
    this.planType = planType;
  }

  public PlanMedicare serviceAreaId(String serviceAreaId) {
    this.serviceAreaId = serviceAreaId;
    return this;
  }

   /**
   * Foreign key for service area
   * @return serviceAreaId
  **/
  @ApiModelProperty(example = "null", value = "Foreign key for service area")
  public String getServiceAreaId() {
    return serviceAreaId;
  }

  public void setServiceAreaId(String serviceAreaId) {
    this.serviceAreaId = serviceAreaId;
  }

  public PlanMedicare source(String source) {
    this.source = source;
    return this;
  }

   /**
   * Source of the plan benefit data
   * @return source
  **/
  @ApiModelProperty(example = "null", value = "Source of the plan benefit data")
  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public PlanMedicare id(String id) {
    this.id = id;
    return this;
  }

   /**
   * Government-issued MedicareAdvantage plan ID
   * @return id
  **/
  @ApiModelProperty(example = "null", value = "Government-issued MedicareAdvantage plan ID")
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PlanMedicare planMedicare = (PlanMedicare) o;
    return Objects.equals(this.carrierName, planMedicare.carrierName) &&
        Objects.equals(this.displayName, planMedicare.displayName) &&
        Objects.equals(this.effectiveDate, planMedicare.effectiveDate) &&
        Objects.equals(this.expirationDate, planMedicare.expirationDate) &&
        Objects.equals(this.identifiers, planMedicare.identifiers) &&
        Objects.equals(this.name, planMedicare.name) &&
        Objects.equals(this.networkIds, planMedicare.networkIds) &&
        Objects.equals(this.networkSize, planMedicare.networkSize) &&
        Objects.equals(this.planType, planMedicare.planType) &&
        Objects.equals(this.serviceAreaId, planMedicare.serviceAreaId) &&
        Objects.equals(this.source, planMedicare.source) &&
        Objects.equals(this.id, planMedicare.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(carrierName, displayName, effectiveDate, expirationDate, identifiers, name, networkIds, networkSize, planType, serviceAreaId, source, id);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PlanMedicare {\n");
    
    sb.append("    carrierName: ").append(toIndentedString(carrierName)).append("\n");
    sb.append("    displayName: ").append(toIndentedString(displayName)).append("\n");
    sb.append("    effectiveDate: ").append(toIndentedString(effectiveDate)).append("\n");
    sb.append("    expirationDate: ").append(toIndentedString(expirationDate)).append("\n");
    sb.append("    identifiers: ").append(toIndentedString(identifiers)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    networkIds: ").append(toIndentedString(networkIds)).append("\n");
    sb.append("    networkSize: ").append(toIndentedString(networkSize)).append("\n");
    sb.append("    planType: ").append(toIndentedString(planType)).append("\n");
    sb.append("    serviceAreaId: ").append(toIndentedString(serviceAreaId)).append("\n");
    sb.append("    source: ").append(toIndentedString(source)).append("\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
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

