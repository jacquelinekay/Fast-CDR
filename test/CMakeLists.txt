if(NOT ((MSVC OR MSVC_IDE) AND EPROSIMA_INSTALLER))
    include(${PROJECT_SOURCE_DIR}/cmake/dev/gtest.cmake)

    # If Windows, link against export dll functions
    if(WIN32)
        add_definitions(-DFASTCDR_DYN_LINK)
    endif()


    ###############################################################################
    # Unit tests
    ###############################################################################
    set(UNITTESTS_SOURCE SimpleTest.cpp ResizeTest.cpp)
    add_executable(UnitTests ${UNITTESTS_SOURCE})
    add_gtest(UnitTests ${UNITTESTS_SOURCE})
    target_include_directories(UnitTests PRIVATE ${GTEST_INCLUDE_DIRS})
    target_link_libraries(UnitTests fastcdr ${GTEST_BOTH_LIBRARIES})
endif()
