/**
 * PDF Watermarking Application - JavaScript Module
 * 
 * Handles file upload functionality, drag-and-drop support,
 * progress tracking, and user interface interactions.
 */

class PDFWatermarkApp {
    constructor() {
        this.currentSessionId = null;
        this.uploadInProgress = false;
        this.progressCheckInterval = null;
        this.maxFileSize = 50 * 1024 * 1024; // 50MB
        this.allowedTypes = ['application/pdf'];
        
        this.initializeElements();
        this.bindEvents();
        this.initializeDragAndDrop();
        
        console.log('PDF Watermarking Application initialized');
    }
    
    /**
     * Initialize DOM element references
     */
    initializeElements() {
        // Upload elements
        this.uploadArea = document.getElementById('upload-area');
        this.fileInput = document.getElementById('file-input');
        this.uploadButton = document.getElementById('upload-button');
        this.dragOverlay = document.getElementById('drag-overlay');
        
        // Progress elements
        this.progressContainer = document.getElementById('upload-progress');
        this.progressFilename = document.getElementById('progress-filename');
        this.progressPercentage = document.getElementById('progress-percentage');
        this.progressBar = document.getElementById('progress-bar');
        this.progressStatus = document.getElementById('progress-status');
        
        // Success elements
        this.successContainer = document.getElementById('upload-success');
        this.successMessage = document.getElementById('success-message');
        this.configureWatermarkBtn = document.getElementById('configure-watermark-btn');
        this.uploadAnotherBtn = document.getElementById('upload-another-btn');
        
        // Error elements
        this.errorContainer = document.getElementById('upload-error');
        this.errorMessage = document.getElementById('error-message');
        this.retryUploadBtn = document.getElementById('retry-upload-btn');
        
        // Loading overlay
        this.loadingOverlay = document.getElementById('loading-overlay');
        this.loadingMessage = document.getElementById('loading-message');
        
        // Navigation
        this.navLinks = document.querySelectorAll('.nav-link');
        this.sections = document.querySelectorAll('.section');
        
        // Watermark Configuration Form
        this.watermarkForm = document.getElementById('watermark-config-form');
        this.watermarkText = document.getElementById('watermark-text');
        this.positionRadios = document.querySelectorAll('input[name="position"]');
        this.positionCoordinates = document.getElementById('position-coordinates');
        this.positionX = document.getElementById('position-x');
        this.positionY = document.getElementById('position-y');
        this.fontSizeSlider = document.getElementById('font-size');
        this.fontSizeValue = document.getElementById('font-size-value');
        this.colorPicker = document.getElementById('watermark-color');
        this.colorPreview = document.getElementById('color-preview');
        this.previewButton = document.getElementById('preview-watermark');
        this.applyButton = document.getElementById('apply-watermark');
        this.formErrors = document.getElementById('form-errors');
        this.errorList = document.getElementById('error-list');
    }
    
    /**
     * Bind event listeners
     */
    bindEvents() {
        // File input events
        this.fileInput.addEventListener('change', (e) => this.handleFileSelection(e));
        this.uploadButton.addEventListener('click', () => this.fileInput.click());
        this.uploadArea.addEventListener('click', () => this.fileInput.click());
        
        // Action buttons
        this.uploadAnotherBtn.addEventListener('click', () => this.resetUploadArea());
        this.retryUploadBtn.addEventListener('click', () => this.resetUploadArea());
        this.configureWatermarkBtn.addEventListener('click', () => this.navigateToSection('configure'));
        
        // Navigation
        this.navLinks.forEach(link => {
            link.addEventListener('click', (e) => {
                e.preventDefault();
                const section = link.getAttribute('href').substring(1);
                this.navigateToSection(section);
            });
        });
        
        // Prevent default drag behaviors
        document.addEventListener('dragover', (e) => e.preventDefault());
        document.addEventListener('drop', (e) => e.preventDefault());
        
        // Watermark Configuration Form Events
        if (this.watermarkForm) {
            this.bindWatermarkFormEvents();
        }
    }
    
    /**
     * Initialize drag and drop functionality (Task 44)
     */
    initializeDragAndDrop() {
        this.uploadArea.addEventListener('dragenter', (e) => {
            e.preventDefault();
            this.handleDragEnter();
        });
        
        this.uploadArea.addEventListener('dragover', (e) => {
            e.preventDefault();
            this.handleDragOver();
        });
        
        this.uploadArea.addEventListener('dragleave', (e) => {
            e.preventDefault();
            this.handleDragLeave(e);
        });
        
        this.uploadArea.addEventListener('drop', (e) => {
            e.preventDefault();
            this.handleDrop(e);
        });
    }
    
    /**
     * Handle drag enter event
     */
    handleDragEnter() {
        if (this.uploadInProgress) return;
        this.uploadArea.classList.add('drag-over');
    }
    
    /**
     * Handle drag over event
     */
    handleDragOver() {
        if (this.uploadInProgress) return;
        this.uploadArea.classList.add('drag-over');
    }
    
    /**
     * Handle drag leave event
     */
    handleDragLeave(e) {
        // Only remove drag-over class if we're actually leaving the upload area
        if (!this.uploadArea.contains(e.relatedTarget)) {
            this.uploadArea.classList.remove('drag-over');
        }
    }
    
    /**
     * Handle file drop event
     */
    handleDrop(e) {
        this.uploadArea.classList.remove('drag-over');
        
        if (this.uploadInProgress) {
            this.showNotification('Upload already in progress', 'warning');
            return;
        }
        
        const files = e.dataTransfer.files;
        if (files.length > 0) {
            this.processFile(files[0]);
        }
    }
    
    /**
     * Handle file selection from input
     */
    handleFileSelection(e) {
        const file = e.target.files[0];
        if (file) {
            this.processFile(file);
        }
    }
    
    /**
     * Process selected file with validation
     */
    processFile(file) {
        console.log('Processing file:', file.name, file.type, file.size);
        
        // Validate file type
        if (!this.allowedTypes.includes(file.type) && !file.name.toLowerCase().endsWith('.pdf')) {
            this.showError('Please select a valid PDF file.');
            return;
        }
        
        // Validate file size
        if (file.size > this.maxFileSize) {
            const maxSizeMB = Math.round(this.maxFileSize / (1024 * 1024));
            this.showError(`File size exceeds ${maxSizeMB}MB limit.`);
            return;
        }
        
        // Validate file is not empty
        if (file.size === 0) {
            this.showError('Selected file is empty.');
            return;
        }
        
        // Start upload process
        this.startUpload(file);
    }
    
    /**
     * Start file upload process
     */
    async startUpload(file) {
        try {
            this.uploadInProgress = true;
            this.hideAllStates();
            this.showProgress(file.name);
            
            // Create form data
            const formData = new FormData();
            formData.append('file', file);
            
            // Upload file with progress tracking
            const response = await this.uploadWithProgress(formData, file.name);
            
            if (response.success) {
                this.currentSessionId = response.sessionId;
                this.showSuccess(response.message);
                this.startProgressTracking();
            } else {
                this.showError(response.message || 'Upload failed');
            }
            
        } catch (error) {
            console.error('Upload error:', error);
            this.showError('Upload failed. Please try again.');
        }
    }
    
    /**
     * Upload file with progress tracking
     */
    uploadWithProgress(formData, filename) {
        return new Promise((resolve, reject) => {
            const xhr = new XMLHttpRequest();
            
            // Track upload progress
            xhr.upload.addEventListener('progress', (e) => {
                if (e.lengthComputable) {
                    const percentage = Math.round((e.loaded / e.total) * 100);
                    this.updateProgress(percentage, 'Uploading...');
                }
            });
            
            // Handle response
            xhr.addEventListener('load', () => {
                try {
                    const response = JSON.parse(xhr.responseText);
                    resolve(response);
                } catch (error) {
                    reject(new Error('Invalid response format'));
                }
            });
            
            // Handle errors
            xhr.addEventListener('error', () => {
                reject(new Error('Network error occurred'));
            });
            
            xhr.addEventListener('abort', () => {
                reject(new Error('Upload was cancelled'));
            });
            
            // Start upload
            xhr.open('POST', '/api/upload');
            xhr.send(formData);
        });
    }
    
    /**
     * Start tracking upload progress via API
     */
    startProgressTracking() {
        if (!this.currentSessionId) return;
        
        this.progressCheckInterval = setInterval(async () => {
            try {
                const response = await fetch(`/api/upload/progress/${this.currentSessionId}`);
                const data = await response.json();
                
                console.log('Progress update:', data);
                
                if (data.status === 'completed') {
                    this.updateProgress(100, data.message);
                    this.stopProgressTracking();
                    setTimeout(() => {
                        this.showFinalSuccess(data.message);
                    }, 500);
                } else if (data.status === 'failed' || data.status === 'error') {
                    this.stopProgressTracking();
                    this.showError(data.message);
                } else if (data.status === 'processing') {
                    this.updateProgress(data.progress, data.message);
                }
                
            } catch (error) {
                console.error('Progress tracking error:', error);
                this.stopProgressTracking();
                this.showError('Failed to track upload progress');
            }
        }, 1000); // Check every second
    }
    
    /**
     * Stop progress tracking
     */
    stopProgressTracking() {
        if (this.progressCheckInterval) {
            clearInterval(this.progressCheckInterval);
            this.progressCheckInterval = null;
        }
    }
    
    /**
     * Show progress state (Task 45)
     */
    showProgress(filename) {
        this.hideAllStates();
        this.progressFilename.textContent = filename;
        this.progressContainer.classList.add('active');
        this.updateProgress(0, 'Preparing upload...');
    }
    
    /**
     * Update progress indicators (Task 45)
     */
    updateProgress(percentage, status) {
        this.progressPercentage.textContent = `${percentage}%`;
        this.progressBar.style.width = `${percentage}%`;
        this.progressStatus.textContent = status;
    }
    
    /**
     * Show success state
     */
    showSuccess(message) {
        this.hideAllStates();
        this.successMessage.textContent = message;
        this.successContainer.classList.add('active');
    }
    
    /**
     * Show final success state after processing
     */
    showFinalSuccess(message) {
        this.showSuccess(message || 'Your PDF has been uploaded and is ready for watermarking!');
        this.uploadInProgress = false;
    }
    
    /**
     * Show error state
     */
    showError(message) {
        this.hideAllStates();
        this.errorMessage.textContent = message;
        this.errorContainer.classList.add('active');
        this.uploadInProgress = false;
        this.stopProgressTracking();
    }
    
    /**
     * Hide all upload states
     */
    hideAllStates() {
        this.progressContainer.classList.remove('active');
        this.successContainer.classList.remove('active');
        this.errorContainer.classList.remove('active');
        this.uploadArea.classList.remove('drag-over');
    }
    
    /**
     * Reset upload area to initial state
     */
    resetUploadArea() {
        this.hideAllStates();
        this.fileInput.value = '';
        this.uploadInProgress = false;
        this.currentSessionId = null;
        this.stopProgressTracking();
        console.log('Upload area reset');
    }
    
    /**
     * Show loading overlay
     */
    showLoading(message = 'Processing...') {
        this.loadingMessage.textContent = message;
        this.loadingOverlay.classList.add('active');
    }
    
    /**
     * Hide loading overlay
     */
    hideLoading() {
        this.loadingOverlay.classList.remove('active');
    }
    
    /**
     * Navigate to different sections
     */
    navigateToSection(sectionName) {
        // Update navigation
        this.navLinks.forEach(link => {
            link.classList.remove('active');
            if (link.getAttribute('href') === `#${sectionName}`) {
                link.classList.add('active');
            }
        });
        
        // Update sections
        this.sections.forEach(section => {
            section.classList.remove('active');
            if (section.id === `${sectionName}-section`) {
                section.classList.add('active');
            }
        });
        
        console.log(`Navigated to section: ${sectionName}`);
    }
    
    /**
     * Show notification (utility function)
     */
    showNotification(message, type = 'info') {
        // Simple console logging for now - can be enhanced with toast notifications
        console.log(`${type.toUpperCase()}: ${message}`);
        
        // Could implement toast notifications here in the future
        if (type === 'error') {
            this.showError(message);
        }
    }
    
    /**
     * Validate session and check if upload is complete
     */
    async validateSession() {
        if (!this.currentSessionId) return false;
        
        try {
            const response = await fetch(`/api/upload/progress/${this.currentSessionId}`);
            const data = await response.json();
            return data.status === 'completed';
        } catch (error) {
            console.error('Session validation error:', error);
            return false;
        }
    }
    
    /**
     * Handle browser navigation and refresh
     */
    handlePageVisibility() {
        document.addEventListener('visibilitychange', () => {
            if (document.hidden) {
                // Page is hidden, pause progress tracking if needed
                console.log('Page visibility changed: hidden');
            } else {
                // Page is visible, resume operations if needed
                console.log('Page visibility changed: visible');
                if (this.currentSessionId && this.uploadInProgress) {
                    this.startProgressTracking();
                }
            }
        });
    }
    
    /**
     * Handle keyboard shortcuts
     */
    initializeKeyboardShortcuts() {
        document.addEventListener('keydown', (e) => {
            // Escape key to cancel/reset
            if (e.key === 'Escape' && this.uploadInProgress) {
                this.resetUploadArea();
            }
            
            // Enter key on upload area to trigger file selection
            if (e.key === 'Enter' && e.target === this.uploadArea) {
                this.fileInput.click();
            }
        });
    }
    
    /**
     * Bind watermark configuration form events (Tasks 47-51)
     */
    bindWatermarkFormEvents() {
        // Position radio button events
        this.positionRadios.forEach(radio => {
            radio.addEventListener('change', () => this.handlePositionChange());
        });
        
        // Font size slider events
        this.fontSizeSlider.addEventListener('input', () => this.handleFontSizeChange());
        
        // Color picker events
        this.colorPicker.addEventListener('change', () => this.handleColorChange());
        
        // Form validation on input
        this.watermarkText.addEventListener('input', () => this.validateField('text'));
        this.positionX.addEventListener('input', () => this.validateField('position'));
        this.positionY.addEventListener('input', () => this.validateField('position'));
        
        // Form submission
        this.watermarkForm.addEventListener('submit', (e) => this.handleFormSubmit(e));
        
        // Preview button
        this.previewButton.addEventListener('click', () => this.handlePreview());
        
        // Initialize default values
        this.handlePositionChange();
        this.handleFontSizeChange();
        this.handleColorChange();
    }
    
    /**
     * Handle position selection change (Task 48)
     */
    handlePositionChange() {
        const selectedPosition = document.querySelector('input[name="position"]:checked').value;
        
        if (selectedPosition === 'fixed') {
            this.positionCoordinates.style.display = 'block';
            this.positionX.required = true;
            this.positionY.required = true;
        } else {
            this.positionCoordinates.style.display = 'none';
            this.positionX.required = false;
            this.positionY.required = false;
            this.positionX.value = '';
            this.positionY.value = '';
        }
        
        this.clearFieldError('position');
    }
    
    /**
     * Handle font size slider change (Task 49)
     */
    handleFontSizeChange() {
        const fontSize = this.fontSizeSlider.value;
        this.fontSizeValue.textContent = `${fontSize}px`;
        this.clearFieldError('fontSize');
    }
    
    /**
     * Handle color picker change (Task 50)
     */
    handleColorChange() {
        const color = this.colorPicker.value;
        this.colorPreview.textContent = color.toUpperCase();
        this.clearFieldError('color');
    }
    
    /**
     * Validate individual form field (Task 51)
     */
    validateField(fieldName) {
        let isValid = true;
        let errorMessage = '';
        
        switch (fieldName) {
            case 'text':
                const text = this.watermarkText.value.trim();
                if (!text) {
                    errorMessage = 'Watermark text is required';
                    isValid = false;
                } else if (text.length > 100) {
                    errorMessage = 'Watermark text must be less than 100 characters';
                    isValid = false;
                }
                break;
                
            case 'position':
                const selectedPosition = document.querySelector('input[name="position"]:checked').value;
                if (selectedPosition === 'fixed') {
                    const x = parseFloat(this.positionX.value);
                    const y = parseFloat(this.positionY.value);
                    
                    if (isNaN(x) || x < 0) {
                        errorMessage = 'X position must be a positive number';
                        isValid = false;
                    } else if (isNaN(y) || y < 0) {
                        errorMessage = 'Y position must be a positive number';
                        isValid = false;
                    }
                }
                break;
                
            case 'fontSize':
                const fontSize = parseInt(this.fontSizeSlider.value);
                if (fontSize < 8 || fontSize > 72) {
                    errorMessage = 'Font size must be between 8 and 72 pixels';
                    isValid = false;
                }
                break;
                
            case 'color':
                const color = this.colorPicker.value;
                if (!color || !color.match(/^#[0-9A-Fa-f]{6}$/)) {
                    errorMessage = 'Please select a valid color';
                    isValid = false;
                }
                break;
        }
        
        if (!isValid) {
            this.showFieldError(fieldName, errorMessage);
        } else {
            this.clearFieldError(fieldName);
        }
        
        return isValid;
    }
    
    /**
     * Validate entire form (Task 51)
     */
    validateForm() {
        const fields = ['text', 'position', 'fontSize', 'color'];
        let allValid = true;
        const errors = [];
        
        fields.forEach(field => {
            if (!this.validateField(field)) {
                allValid = false;
            }
        });
        
        // Additional form-level validations
        if (!this.currentSessionId) {
            errors.push('Please upload a PDF file first');
            allValid = false;
        }
        
        if (!allValid) {
            this.showFormErrors(errors);
        } else {
            this.hideFormErrors();
        }
        
        return allValid;
    }
    
    /**
     * Show field error (Task 51)
     */
    showFieldError(fieldName, message) {
        const errorElement = document.getElementById(`${fieldName}-error`);
        if (errorElement) {
            errorElement.textContent = message;
            errorElement.classList.add('show');
        }
    }
    
    /**
     * Clear field error (Task 51)
     */
    clearFieldError(fieldName) {
        const errorElement = document.getElementById(`${fieldName}-error`);
        if (errorElement) {
            errorElement.textContent = '';
            errorElement.classList.remove('show');
        }
    }
    
    /**
     * Show form errors (Task 51)
     */
    showFormErrors(errors) {
        if (errors.length === 0) return;
        
        this.errorList.innerHTML = '';
        errors.forEach(error => {
            const li = document.createElement('li');
            li.textContent = error;
            this.errorList.appendChild(li);
        });
        
        this.formErrors.style.display = 'block';
    }
    
    /**
     * Hide form errors (Task 51)
     */
    hideFormErrors() {
        this.formErrors.style.display = 'none';
    }
    
    /**
     * Get watermark configuration from form
     */
    getWatermarkConfig() {
        const selectedPosition = document.querySelector('input[name="position"]:checked').value;
        const positionConfig = selectedPosition === 'random' 
            ? { type: 'random' }
            : { 
                type: 'fixed', 
                x: parseFloat(this.positionX.value), 
                y: parseFloat(this.positionY.value) 
              };
        
        return {
            text: this.watermarkText.value.trim(),
            position: positionConfig,
            orientation: { type: 'fixed', angle: 0 }, // Default for basic implementation
            fontSize: { type: 'fixed', size: parseInt(this.fontSizeSlider.value) },
            color: { type: 'fixed', color: this.colorPicker.value },
            quantity: 1 // Default for basic implementation
        };
    }
    
    /**
     * Handle form preview (Tasks 47-51)
     */
    async handlePreview() {
        if (!this.validateForm()) return;
        
        const config = this.getWatermarkConfig();
        console.log('Preview watermark config:', config);
        
        // For now, show a notification - preview functionality can be enhanced later
        this.showNotification('Preview functionality will be available in a future update', 'info');
    }
    
    /**
     * Handle form submission (Tasks 47-51)
     */
    async handleFormSubmit(e) {
        e.preventDefault();
        
        if (!this.validateForm()) return;
        
        try {
            this.showLoading('Applying watermark...');
            
            const config = this.getWatermarkConfig();
            console.log('Applying watermark config:', config);
            
            const response = await fetch('/api/watermark/apply', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    sessionId: this.currentSessionId,
                    config: config
                })
            });
            
            const result = await response.json();
            
            if (result.success) {
                this.hideLoading();
                this.navigateToSection('download');
                this.showNotification('Watermark applied successfully!', 'success');
            } else {
                this.hideLoading();
                this.showNotification(result.message || 'Failed to apply watermark', 'error');
            }
            
        } catch (error) {
            console.error('Watermark application error:', error);
            this.hideLoading();
            this.showNotification('Failed to apply watermark. Please try again.', 'error');
        }
    }
    
    /**
     * Initialize the application
     */
    init() {
        this.handlePageVisibility();
        this.initializeKeyboardShortcuts();
        console.log('Application fully initialized');
    }
}

// Initialize application when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    const app = new PDFWatermarkApp();
    app.init();
    
    // Make app globally accessible for debugging
    window.pdfApp = app;
});

// Handle page unload to cleanup resources
window.addEventListener('beforeunload', () => {
    if (window.pdfApp) {
        window.pdfApp.stopProgressTracking();
    }
});