package com.catalyst.Home.Services.Impl;

import com.catalyst.Home.Dto.response.GrpcResponse;
import com.catalyst.Home.Entity.BusinessDetails;
import com.catalyst.Home.Entity.Documents;
import com.catalyst.Home.Entity.Employees;
import com.catalyst.Home.Entity.FarmProducts;
import com.catalyst.Home.Entity.FarmersAddress;
import com.catalyst.Home.Entity.FarmersDetails;
import com.catalyst.Home.Entity.FarmersKyc;
import com.catalyst.Home.Entity.Farms;
import com.catalyst.Home.Entity.Users;
import com.catalyst.Home.Repositories.AddressRepository;
import com.catalyst.Home.Repositories.BusinessRepository;
import com.catalyst.Home.Repositories.DocumentsRepository;
import com.catalyst.Home.Repositories.EmployeesRepository;
import com.catalyst.Home.Repositories.FarmProductsRepository;
import com.catalyst.Home.Repositories.FarmerRepository;
import com.catalyst.Home.Repositories.FarmersKycRepository;
import com.catalyst.Home.Repositories.FarmsRepository;
import com.catalyst.Home.Repositories.UsersRepository;
import com.catalyst.Home.Services.OnboardingService;
import com.catalyst.Home.Utils.Role;
import com.catalyst.Home.Utils.VerificationStatus;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import users.AddressOuterClass;
import users.Business;
import users.DocumentOuterClass;
import users.Farmers;
import users.KycOuterClass;
import users.ProductsOuterClass;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class OnboardingServiceImpl implements OnboardingService {
    private final FarmerRepository farmerRepository;
    private final UsersRepository usersRepository;
    private final AddressRepository addressRepository;
    private final FarmersKycRepository farmersKycRepository;
    private final DocumentsRepository documentsRepository;
    private final FarmsRepository farmsRepository;
    private final EmployeesRepository employeesRepository;
    private final FarmProductsRepository farmProductsRepository;
    private final BusinessRepository businessRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public GrpcResponse<Farmers.CreateFarmerResponse> createFarmer(Farmers.CreateFarmerRequest request) {
        Optional<FarmersDetails> customerByEmail = farmerRepository.findByEmail(request.getEmail());

        if (customerByEmail.isPresent() || usersRepository.findByEmail(request.getEmail()).isPresent()) {
            return GrpcResponse.<Farmers.CreateFarmerResponse>builder()
                    .message("Farmer with email already exists")
                    .code("401")
                    .status(Status.Code.ALREADY_EXISTS)
                    .build();
        }

        Optional<FarmersDetails> customerByPhone = farmerRepository.findByContactPhoneNumber(request.getPhone());

        if (customerByPhone.isPresent() || usersRepository.findByPhoneNumber(request.getPhone()).isPresent()) {
            return GrpcResponse.<Farmers.CreateFarmerResponse>builder()
                    .message("Farmer with phone number already exists")
                    .code("401")
                    .status(Status.Code.ALREADY_EXISTS)
                    .build();
        }

        Users user = usersRepository.save(Users.builder()
                .email(request.getEmail())
                .phoneNumber(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.FARMER)
                .enabled(true)
                .accountNonLocked(true)
                .build());

        FarmersDetails farmer = FarmersDetails.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .contactPhoneNumber(request.getPhone())
                .email(request.getEmail())
                .active(true)
                .sectorCode("AGRIC")
                .nationality(request.getCountry())
                .maritalStatus("Single")
                .uuid(UUID.randomUUID().toString())
                .emailValidated(false)
                .phoneValidated(false)
                .user(user)
                .createdAt(LocalDate.now().toString())
                .build();

        farmerRepository.save(farmer);

        Farmers.CreateFarmerResponse response = Farmers.CreateFarmerResponse.newBuilder()
                .setCode("200")
                .setMessage("farmer account created successfully")
                .build();

        return GrpcResponse.<Farmers.CreateFarmerResponse>builder()
                .code("200")
                .message("farmer account created successfully")
                .status(Status.Code.OK)
                .data(response)
                .build();
    }

    @Override
    @Transactional
    public GrpcResponse<Farmers.AddFarmerKycResponse> addFarmerKyc(Farmers.AddFarmerKycRequest request) {
        Optional<FarmersDetails> farmerOpt = farmerRepository.findByUuid(request.getId());
        if (farmerOpt.isEmpty()) {
            return GrpcResponse.<Farmers.AddFarmerKycResponse>builder()
                    .code("404").message("Farmer not found").status(Status.Code.NOT_FOUND).build();
        }
        FarmersDetails farmer = farmerOpt.get();

        Farmers.CorporateUserAddress corporateAddress = request.getCorporateCustomerAddress();
        FarmersAddress address = addressRepository.save(FarmersAddress.builder()
                .residentialAddress(corporateAddress.getAddress())
                .city(corporateAddress.getCity())
                .state(corporateAddress.getState())
                .country(corporateAddress.getCountry())
                .uuid(UUID.randomUUID().toString())
                .build());

        FarmersKyc kyc = farmersKycRepository.save(FarmersKyc.builder()
                .signature(request.getSignature())
                .bvn(request.getBvn())
                .tin(request.getTin())
                .passportPhotograph(request.getPassportPhotograph())
                .kycLevel(request.getKycLevel())
                .tier(request.getTier())
                .tinVerified(request.getTinVerified())
                .drivingLicense(request.getDrivingLicense())
                .internationalPassportNumber(request.getInternationalPassportNumber())
                .votersCardNumber(request.getVotersCardNumber())
                .individualFarmerAddress(address)
                .uuid(UUID.randomUUID().toString())
                .build());

        for (DocumentOuterClass.Document doc : request.getDocumentsAndIdentityList()) {
            documentsRepository.save(Documents.builder()
                    .documentName(doc.getDocumentName())
                    .documentUrl(doc.getDocumentUrl())
                    .documentNumber(doc.getDocumentNumber())
                    .countryOfIssuance(doc.getCountryOfIssuance())
                    .placeOfIssuance(doc.getPlaceOfIssuance())
                    .expirationDate(parseDate(doc.getExpirationDate()))
                    .dateOfIssuance(parseDate(doc.getDateOfIssuance()))
                    .verificationStatus(mapVerificationStatus(doc.getVerificationStatus()))
                    .createdAt(LocalDate.now().toString())
                    .uuid(UUID.randomUUID().toString())
                    .kyc(kyc)
                    .build());
        }

        farmer.setKyc(kyc);
        farmerRepository.save(farmer);

        Farmers.AddFarmerKycResponse response = Farmers.AddFarmerKycResponse.newBuilder()
                .setCode("200")
                .setMessage("KYC information added successfully")
                .build();

        return GrpcResponse.<Farmers.AddFarmerKycResponse>builder()
                .code("200")
                .message("KYC information added successfully")
                .status(Status.Code.OK)
                .data(response)
                .build();
    }

    @Override
    @Transactional
    public GrpcResponse<Farmers.AddFarmResponse> addFarm(Farmers.AddFarmRequest request) {
        Optional<FarmersDetails> farmerOpt = farmerRepository.findByUuid(request.getUserId());
        if (farmerOpt.isEmpty()) {
            return GrpcResponse.<Farmers.AddFarmResponse>builder()
                    .code("404").message("Farmer not found").status(Status.Code.NOT_FOUND).build();
        }
        FarmersDetails farmer = farmerOpt.get();

        Farms farm = farmsRepository.save(Farms.builder()
                .address(request.getAddress())
                .lga(request.getLga())
                .state(request.getState())
                .country(request.getCountry())
                .landSize(request.getLandSize())
                .isLease(request.getIsLease())
                .isOwnProperty(request.getIsOwnProperty())
                .leaseYears(request.getLeaseYears())
                .numOfEmployees(request.getNumOfEmployees())
                .plantType(request.getPlantType())
                .plantSeasonStartDate(request.getPlantSeasonStartDate())
                .plantSeasonEndDate(request.getPlantSeasonEndDate())
                .farmType(request.getFarmType())
                .uuid(UUID.randomUUID().toString())
                .farmers(farmer)
                .build());

        for (Farmers.Employee employee : request.getEmployeesList()) {
            employeesRepository.save(Employees.builder()
                    .occupation(employee.getOccupation())
                    .businessCategory(employee.getBusinessCategory())
                    .employmentCategory(employee.getEmploymentCategory())
                    .startDate(employee.getStartDate())
                    .endDate(employee.getEndDate())
                    .durationOfEmployment(employee.getDurationOfEmployment())
                    .employerPhone(employee.getEmployerPhone())
                    .position(employee.getPosition())
                    .active(true)
                    .uuid(UUID.randomUUID().toString())
                    .farmers(farmer)
                    .farm(farm)
                    .build());
        }

        Farmers.AddFarmResponse response = Farmers.AddFarmResponse.newBuilder()
                .setCode("200")
                .setMessage("Farm added successfully")
                .build();

        return GrpcResponse.<Farmers.AddFarmResponse>builder()
                .code("200")
                .message("Farm added successfully")
                .status(Status.Code.OK)
                .data(response)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Farmers.FarmerProfileResponse> getFarmersProfile(Farmers.FarmerProfileRequest request) {
        Optional<FarmersDetails> farmerOpt = farmerRepository.findByUuid(request.getUserId());
        if (farmerOpt.isEmpty()) {
            return GrpcResponse.<Farmers.FarmerProfileResponse>builder()
                    .code("404").message("Farmer not found").status(Status.Code.NOT_FOUND).build();
        }

        Farmers.FarmerProfileResponse response = Farmers.FarmerProfileResponse.newBuilder()
                .setFarmerDetails(toFarmerProto(farmerOpt.get()))
                .build();

        return GrpcResponse.<Farmers.FarmerProfileResponse>builder()
                .code("200")
                .message("Profile retrieved successfully")
                .status(Status.Code.OK)
                .data(response)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Farmers.GetFarmsResponse> getFarms(Farmers.GetFarmsRequest request) {
        List<Farms> farms = farmsRepository.findByFarmers_Uuid(request.getUserId());

        Farmers.GetFarmsResponse response = Farmers.GetFarmsResponse.newBuilder()
                .addAllFarms(farms.stream().map(this::toFarmProto).toList())
                .build();

        return GrpcResponse.<Farmers.GetFarmsResponse>builder()
                .code("200")
                .message("Farms retrieved successfully")
                .status(Status.Code.OK)
                .data(response)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<KycOuterClass.KYCStatus> checkKycStatus(KycOuterClass.KYCRequest request) {
        Optional<FarmersDetails> farmerOpt = farmerRepository.findByUuid(request.getUserId());
        if (farmerOpt.isEmpty()) {
            return GrpcResponse.<KycOuterClass.KYCStatus>builder()
                    .code("404").message("Farmer not found").status(Status.Code.NOT_FOUND).build();
        }

        FarmersKyc kyc = farmerOpt.get().getKyc();
        if (kyc == null) {
            KycOuterClass.KYCStatus status = KycOuterClass.KYCStatus.newBuilder()
                    .setVerificationStatus(users.Enum.VerificationStatus.NULL)
                    .setMessage("KYC not started")
                    .build();
            return GrpcResponse.<KycOuterClass.KYCStatus>builder()
                    .code("200").message("KYC not started").status(Status.Code.OK).data(status).build();
        }

        List<Documents> documents = kyc.getDocumentsAndIdentity();
        users.Enum.VerificationStatus overall;
        String message;
        if (documents == null || documents.isEmpty()) {
            overall = users.Enum.VerificationStatus.PENDING;
            message = "KYC submitted, awaiting document upload";
        } else if (documents.stream().anyMatch(d -> d.getVerificationStatus() == VerificationStatus.REJECTED)) {
            overall = users.Enum.VerificationStatus.REJECTED;
            message = "One or more documents were rejected";
        } else if (documents.stream().allMatch(d -> d.getVerificationStatus() == VerificationStatus.APPROVED)) {
            overall = users.Enum.VerificationStatus.APPROVED;
            message = "KYC approved";
        } else {
            overall = users.Enum.VerificationStatus.PENDING;
            message = "KYC under review";
        }

        KycOuterClass.KYCStatus status = KycOuterClass.KYCStatus.newBuilder()
                .setVerificationStatus(overall)
                .setMessage(message)
                .build();

        return GrpcResponse.<KycOuterClass.KYCStatus>builder()
                .code("200").message(message).status(Status.Code.OK).data(status).build();
    }

    @Override
    @Transactional
    public GrpcResponse<Business.CreateBusinessResponse> createBusiness(Business.CreateBusinessRequest request) {
        if (businessRepository.findByCompanyRegistrationNo(request.getCompanyRegistrationNo()).isPresent()) {
            return GrpcResponse.<Business.CreateBusinessResponse>builder()
                    .code("409").message("Business with this registration number already exists")
                    .status(Status.Code.ALREADY_EXISTS).build();
        }

        BusinessDetails business = BusinessDetails.builder()
                .companyName(request.getCompanyName())
                .companyRegistrationNo(request.getCompanyRegistrationNo())
                .dateOfRegistration(request.getDateOfRegistration())
                .countryOfRegistration(request.getCountryOfRegistration())
                .companyAddress(request.getCompanyAddress())
                .state(request.getState())
                .city(request.getCity())
                .country(request.getCountry())
                .lastName(request.getLastName())
                .bvn(request.getBvn())
                .tin(request.getTin())
                .address(request.getAddress())
                .uuid(UUID.randomUUID().toString())
                .createdAt(LocalDate.now().toString())
                .build();

        businessRepository.save(business);

        Business.CreateBusinessResponse response = Business.CreateBusinessResponse.newBuilder()
                .setCode("200")
                .setMessage("Business account created successfully")
                .build();

        return GrpcResponse.<Business.CreateBusinessResponse>builder()
                .code("200")
                .message("Business account created successfully")
                .status(Status.Code.OK)
                .data(response)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Business.GetBusinessProfileResponse> getBusinessProfile(Business.GetBusinessProfileRequest request) {
        Optional<BusinessDetails> businessOpt = businessRepository.findByUuid(request.getUserId());
        if (businessOpt.isEmpty()) {
            return GrpcResponse.<Business.GetBusinessProfileResponse>builder()
                    .code("404").message("Business not found").status(Status.Code.NOT_FOUND).build();
        }

        BusinessDetails business = businessOpt.get();
        Business.GetBusinessProfileResponse response = Business.GetBusinessProfileResponse.newBuilder()
                .setCompanyName(nullToEmpty(business.getCompanyName()))
                .setCompanyRegistrationNo(nullToEmpty(business.getCompanyRegistrationNo()))
                .setDateOfRegistration(nullToEmpty(business.getDateOfRegistration()))
                .setCountryOfRegistration(nullToEmpty(business.getCountryOfRegistration()))
                .setCompanyAddress(nullToEmpty(business.getCompanyAddress()))
                .setState(nullToEmpty(business.getState()))
                .setCity(nullToEmpty(business.getCity()))
                .setCountry(nullToEmpty(business.getCountry()))
                .setLastName(nullToEmpty(business.getLastName()))
                .setBvn(nullToEmpty(business.getBvn()))
                .setTin(nullToEmpty(business.getTin()))
                .setAddress(nullToEmpty(business.getAddress()))
                .build();

        return GrpcResponse.<Business.GetBusinessProfileResponse>builder()
                .code("200")
                .message("Business profile retrieved successfully")
                .status(Status.Code.OK)
                .data(response)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<ProductsOuterClass.GetProductsResponse> getFarmProducts(ProductsOuterClass.GetProductsRequest request) {
        List<FarmProducts> products = farmProductsRepository.findByFarm_Uuid(request.getFarmId());

        ProductsOuterClass.GetProductsResponse response = ProductsOuterClass.GetProductsResponse.newBuilder()
                .addAllProduct(products.stream().map(this::toProductProto).toList())
                .build();

        return GrpcResponse.<ProductsOuterClass.GetProductsResponse>builder()
                .code("200")
                .message("Products retrieved successfully")
                .status(Status.Code.OK)
                .data(response)
                .build();
    }

    @Override
    @Transactional
    public GrpcResponse<ProductsOuterClass.AddFarmProductResponse> addFarmProduct(ProductsOuterClass.AddFarmProductRequest request) {
        Optional<Farms> farmOpt = farmsRepository.findByUuid(request.getFarmId());
        if (farmOpt.isEmpty()) {
            return GrpcResponse.<ProductsOuterClass.AddFarmProductResponse>builder()
                    .code("404").message("Farm not found").status(Status.Code.NOT_FOUND).build();
        }

        BigDecimal pricePerUnit = BigDecimal.valueOf(request.getPricePerUnit());
        BigDecimal totalValue = pricePerUnit.multiply(BigDecimal.valueOf(request.getQuantity()));

        farmProductsRepository.save(FarmProducts.builder()
                .productName(request.getProductName())
                .category(request.getCategory())
                .productType(request.getProductType())
                .description(request.getDescription())
                .quantity(request.getQuantity())
                .unit(request.getUnit())
                .pricePerUnit(pricePerUnit)
                .totalValue(totalValue)
                .harvestDate(parseDate(request.getHarvestDate()))
                .expiryDate(parseDate(request.getExpiryDate()))
                .isAvailable(request.getIsAvailable())
                .farm(farmOpt.get())
                .build());

        ProductsOuterClass.AddFarmProductResponse response = ProductsOuterClass.AddFarmProductResponse.newBuilder()
                .setCode("200")
                .setMessage("Product added successfully")
                .build();

        return GrpcResponse.<ProductsOuterClass.AddFarmProductResponse>builder()
                .code("200")
                .message("Product added successfully")
                .status(Status.Code.OK)
                .data(response)
                .build();
    }

    private Farmers.Farmer toFarmerProto(FarmersDetails farmer) {
        Farmers.Farmer.Builder builder = Farmers.Farmer.newBuilder()
                .setId(nullToEmpty(farmer.getUuid()))
                .setFirstName(nullToEmpty(farmer.getFirstName()))
                .setLastName(nullToEmpty(farmer.getLastName()))
                .setContactPhoneNumber(nullToEmpty(farmer.getContactPhoneNumber()))
                .setEmail(nullToEmpty(farmer.getEmail()))
                .setMotherMaidenName(nullToEmpty(farmer.getOtherMaidenName()))
                .setMiddleName(nullToEmpty(farmer.getMiddleName()))
                .setDateOfBirth(nullToEmpty(farmer.getDateOfBirth()))
                .setCreatedAt(nullToEmpty(farmer.getCreatedAt()))
                .setUpdatedAt(nullToEmpty(farmer.getUpdatedAt()))
                .setActive(Boolean.TRUE.equals(farmer.getActive()))
                .setSectorCode(nullToEmpty(farmer.getSectorCode()))
                .setSex(nullToEmpty(farmer.getSex()))
                .setTitle(nullToEmpty(farmer.getTitle()))
                .setAlternatePhoneNumber(nullToEmpty(farmer.getAlternatePhoneNumber()))
                .setNationality(nullToEmpty(farmer.getNationality()))
                .setMaritalStatus(nullToEmpty(farmer.getMaritalStatus()))
                .setEducation(nullToEmpty(farmer.getEducation()))
                .setNameOfSpouse(nullToEmpty(farmer.getNameOfSpouse()))
                .setBranch(nullToEmpty(farmer.getBranch()))
                .setFatherName(nullToEmpty(farmer.getFatherName()))
                .setMotherName(nullToEmpty(farmer.getMotherName()))
                .setUseAltAccNum(Boolean.TRUE.equals(farmer.getUseAltAccNum()))
                .setSmsEnabled(Boolean.TRUE.equals(farmer.getSmsEnabled()))
                .setEmailEnabled(Boolean.TRUE.equals(farmer.getEmailEnabled()));

        if (farmer.getKyc() != null) {
            builder.setKyc(toKycProto(farmer.getKyc()));
        }

        if (farmer.getFarms() != null) {
            builder.addAllFarms(farmer.getFarms().stream().map(this::toFarmProto).toList());
        }

        return builder.build();
    }

    private Farmers.Farm toFarmProto(Farms farm) {
        Farmers.Farm.Builder builder = Farmers.Farm.newBuilder()
                .setAddress(nullToEmpty(farm.getAddress()))
                .setLga(nullToEmpty(farm.getLga()))
                .setState(nullToEmpty(farm.getState()))
                .setCountry(nullToEmpty(farm.getCountry()))
                .setLandSize(nullToEmpty(farm.getLandSize()))
                .setIsLease(Boolean.TRUE.equals(farm.getIsLease()))
                .setIsOwnProperty(Boolean.TRUE.equals(farm.getIsOwnProperty()))
                .setLeaseYears(farm.getLeaseYears() == null ? 0 : farm.getLeaseYears())
                .setNumOfEmployees(farm.getNumOfEmployees() == null ? 0 : farm.getNumOfEmployees())
                .setPlantType(nullToEmpty(farm.getPlantType()))
                .setPlantSeasonStartDate(nullToEmpty(farm.getPlantSeasonStartDate()))
                .setPlantSeasonEndDate(nullToEmpty(farm.getPlantSeasonEndDate()))
                .setFarmType(nullToEmpty(farm.getFarmType()));

        if (farm.getFarmPictures() != null) {
            builder.addAllFarmPictures(farm.getFarmPictures());
        }

        return builder.build();
    }

    private KycOuterClass.Kyc toKycProto(FarmersKyc kyc) {
        KycOuterClass.Kyc.Builder builder = KycOuterClass.Kyc.newBuilder()
                .setId(String.valueOf(kyc.getId()))
                .setSignature(nullToEmpty(kyc.getSignature()))
                .setBvn(nullToEmpty(kyc.getBvn()))
                .setPassportPhotograph(nullToEmpty(kyc.getPassportPhotograph()))
                .setKycLevel(nullToEmpty(kyc.getKycLevel()))
                .setTin(nullToEmpty(kyc.getTin()))
                .setBvnVerified(Boolean.TRUE.equals(kyc.getBvnVerified()))
                .setNin(nullToEmpty(kyc.getNin()))
                .setNinVerified(Boolean.TRUE.equals(kyc.getNinVerified()))
                .setTinVerified(Boolean.TRUE.equals(kyc.getTinVerified()))
                .setPassportPhotographVerified(Boolean.TRUE.equals(kyc.getPassportPhotographVerified()))
                .setTier(nullToEmpty(kyc.getTier()))
                .setDrivingLicense(nullToEmpty(kyc.getDrivingLicense()))
                .setDrivingLicenseVerified(Boolean.TRUE.equals(kyc.getDrivingLicenseVerified()))
                .setInternationalPassportNumber(nullToEmpty(kyc.getInternationalPassportNumber()))
                .setInternationalPassportNumberVerified(Boolean.TRUE.equals(kyc.getInternationalPassportVerified()))
                .setVotersCardNumber(nullToEmpty(kyc.getVotersCardNumber()));

        if (kyc.getDocumentsAndIdentity() != null) {
            builder.addAllDocumentsAndIdentity(kyc.getDocumentsAndIdentity().stream()
                    .map(this::toDocumentProto)
                    .toList());
        }

        if (kyc.getIndividualFarmerAddress() != null) {
            builder.setIndividualCustomerAddress(toAddressProto(kyc.getIndividualFarmerAddress()));
        }

        return builder.build();
    }

    private DocumentOuterClass.Document toDocumentProto(Documents doc) {
        DocumentOuterClass.Document.Builder builder = DocumentOuterClass.Document.newBuilder()
                .setId(String.valueOf(doc.getId()))
                .setDocumentName(nullToEmpty(doc.getDocumentName()))
                .setDocumentUrl(nullToEmpty(doc.getDocumentUrl()))
                .setDocumentNumber(nullToEmpty(doc.getDocumentNumber()))
                .setCountryOfIssuance(nullToEmpty(doc.getCountryOfIssuance()))
                .setPlaceOfIssuance(nullToEmpty(doc.getPlaceOfIssuance()))
                .setVerificationStatus(mapVerificationStatus(doc.getVerificationStatus()))
                .setCreatedAt(nullToEmpty(doc.getCreatedAt()));

        if (doc.getExpirationDate() != null) {
            builder.setExpirationDate(doc.getExpirationDate().toString());
        }
        if (doc.getDateOfIssuance() != null) {
            builder.setDateOfIssuance(doc.getDateOfIssuance().toString());
        }

        return builder.build();
    }

    private AddressOuterClass.Address toAddressProto(FarmersAddress address) {
        return AddressOuterClass.Address.newBuilder()
                .setId(String.valueOf(address.getId()))
                .setResidentialAddress(nullToEmpty(address.getResidentialAddress()))
                .setResidency(nullToEmpty(address.getResidency()))
                .setStateOfOrigin(nullToEmpty(address.getStateOfOrigin()))
                .setLgaOfOrigin(nullToEmpty(address.getLgaOfOrigin()))
                .setCity(nullToEmpty(address.getCity()))
                .setPostalCode(nullToEmpty(address.getPostalCode()))
                .setState(nullToEmpty(address.getState()))
                .setCountry(nullToEmpty(address.getCountry()))
                .setVerificationStatus(mapVerificationStatus(address.getVerificationStatus()))
                .build();
    }

    private ProductsOuterClass.Products toProductProto(FarmProducts product) {
        return ProductsOuterClass.Products.newBuilder()
                .setId(String.valueOf(product.getId()))
                .setName(nullToEmpty(product.getProductName()))
                .setCategory(nullToEmpty(product.getCategory()))
                .setType(nullToEmpty(product.getProductType()))
                .setQuantity(product.getQuantity() == null ? 0 : product.getQuantity())
                .setUnit(nullToEmpty(product.getUnit()))
                .setPricePerUnit(product.getPricePerUnit() == null ? 0 : product.getPricePerUnit().doubleValue())
                .setIsAvailable(Boolean.TRUE.equals(product.getIsAvailable()))
                .build();
    }

    private static users.Enum.VerificationStatus mapVerificationStatus(VerificationStatus status) {
        if (status == null) {
            return users.Enum.VerificationStatus.NULL;
        }
        return users.Enum.VerificationStatus.valueOf(status.name());
    }

    private static VerificationStatus mapVerificationStatus(users.Enum.VerificationStatus status) {
        if (status == null || status == users.Enum.VerificationStatus.UNRECOGNIZED) {
            return VerificationStatus.NULL;
        }
        return VerificationStatus.valueOf(status.name());
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
