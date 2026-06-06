#!/bin/bash

# ============================================================================
# Test Runner Script for School Fee App
# ============================================================================
# This script provides convenient commands for running tests locally
# with or without a real database.
# ============================================================================

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Functions
print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Check if Docker is running
check_docker() {
    if ! docker info > /dev/null 2>&1; then
        print_error "Docker is not running. Please start Docker Desktop."
        exit 1
    fi
}

# Start test database
start_test_db() {
    print_info "Starting test database..."
    docker-compose -f docker-compose.test.yml up -d
    
    print_info "Waiting for database to be ready..."
    local retries=0
    local max_retries=20
    
    while [ $retries -lt $max_retries ]; do
        if docker exec school-fee-test-db pg_isready -U test_user -d school_fee_test > /dev/null 2>&1; then
            print_success "Test database is ready!"
            return 0
        fi
        retries=$((retries + 1))
        sleep 1
    done
    
    print_error "Database failed to start within timeout"
    exit 1
}

# Stop test database
stop_test_db() {
    print_info "Stopping test database..."
    docker-compose -f docker-compose.test.yml down
    print_success "Test database stopped"
}

# Run unit tests only (fast, no database needed)
run_unit_tests() {
    print_info "Running unit tests (no database required)..."
    ./gradlew test --tests "*Test" --tests "!*IntegrationTest"
    print_success "Unit tests completed!"
}

# Run integration tests with test database
run_integration_tests() {
    check_docker
    start_test_db
    
    trap stop_test_db EXIT  # Ensure DB is stopped even if tests fail
    
    print_info "Running integration tests with PostgreSQL..."
    
    export SPRING_R2DBC_URL=r2dbc:postgresql://localhost:5433/school_fee_test
    export SPRING_R2DBC_USERNAME=test_user
    export SPRING_R2DBC_PASSWORD=test_pass
    
    ./gradlew test --tests "*IntegrationTest"
    
    print_success "Integration tests completed!"
}

# Run all tests (unit + integration)
run_all_tests() {
    check_docker
    start_test_db
    
    trap stop_test_db EXIT
    
    print_info "Running all tests (unit + integration) with PostgreSQL..."
    
    export SPRING_R2DBC_URL=r2dbc:postgresql://localhost:5433/school_fee_test
    export SPRING_R2DBC_USERNAME=test_user
    export SPRING_R2DBC_PASSWORD=test_pass
    
    ./gradlew clean test jacocoTestReport
    
    print_success "All tests completed!"
    print_info "Coverage report: build/reports/jacoco/test/html/index.html"
    
    # Open coverage report on macOS
    if [[ "$OSTYPE" == "darwin"* ]]; then
        open build/reports/jacoco/test/html/index.html
    fi
}

# Run specific test class
run_specific_test() {
    local test_class=$1
    
    if [ -z "$test_class" ]; then
        print_error "Please provide a test class name"
        echo "Usage: $0 specific com.fee.app.schoolfeeapp.auth.service.impl.UserManagementServiceIntegrationTest"
        exit 1
    fi
    
    check_docker
    start_test_db
    
    trap stop_test_db EXIT
    
    print_info "Running specific test: $test_class"
    
    export SPRING_R2DBC_URL=r2dbc:postgresql://localhost:5433/school_fee_test
    export SPRING_R2DBC_USERNAME=test_user
    export SPRING_R2DBC_PASSWORD=test_pass
    
    ./gradlew test --tests "$test_class"
    
    print_success "Test completed!"
}

# Show usage
show_usage() {
    echo "Usage: $0 <command>"
    echo ""
    echo "Commands:"
    echo "  unit          - Run unit tests only (fast, no database)"
    echo "  integration   - Run integration tests with PostgreSQL"
    echo "  all           - Run all tests (unit + integration)"
    echo "  specific <class> - Run a specific test class"
    echo "  db-start      - Start test database only"
    echo "  db-stop       - Stop test database only"
    echo "  help          - Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 unit"
    echo "  $0 integration"
    echo "  $0 all"
    echo "  $0 specific com.fee.app.schoolfeeapp.auth.service.impl.UserManagementServiceIntegrationTest"
    echo ""
}

# Main command handling
case "${1:-help}" in
    unit)
        run_unit_tests
        ;;
    integration)
        run_integration_tests
        ;;
    all)
        run_all_tests
        ;;
    specific)
        run_specific_test "$2"
        ;;
    db-start)
        check_docker
        start_test_db
        ;;
    db-stop)
        stop_test_db
        ;;
    help|*)
        show_usage
        ;;
esac
