# Requirements Analysis Prompt

Transform the provided high-level requirements into a comprehensive, structured requirements document using the following methodology:

## Instructions:

1. **Create a Requirements Document** with the following structure:
    - Document title: "Requirements Document"
    - Introduction section that summarizes the application purpose and key functionality
    - Requirements section with numbered requirements

2. **For each major feature or functionality mentioned**, create a separate requirement following this format:
    - **Requirement [Number]**: Use sequential numbering (1, 2, 3, etc.)
    - **User Story**: Write in the format "As a user, I want [goal] so that [benefit/reason]"
    - **Acceptance Criteria**: List specific, testable criteria using "WHEN [condition] THEN the system SHALL [expected behavior]" format

3. **Guidelines for creating User Stories**:
    - Focus on user goals and benefits
    - Keep them concise but descriptive
    - Ensure each story represents a complete user interaction or need

4. **Guidelines for Acceptance Criteria**:
    - Use formal language with "SHALL" statements for precision
    - Make each criterion specific and testable
    - Cover normal flows, edge cases, and error conditions where applicable
    - Include UI/UX considerations when relevant
    - Address data persistence and loading requirements
    - Consider user feedback and error handling

5. **Document Organization**:
    - Group related functionality into logical requirements
    - Ensure comprehensive coverage of all mentioned features
    - Maintain consistent formatting throughout
    - Use clear, professional language

## Input:
Implement a simple web application to upload pdf files and add watermarks with the following requirements that the user can configure in web ui.
Watermarks should:
* Be fixed or randomly positioned
* Fixed or random orientation
* Fixed or random font size
* Color of each letter fixed or random
* Define the number of watermarks to include in the final document
  Use Scala, ZIO, ZIO HTTP for the application, and PDFbox to implement operations with pdf files.
  Consult the documentation to use the correct APIs and dependency versions.

## Output Format:
A complete requirements document following the structure and formatting guidelines above, ready for use in software development planning and implementation.

Write the requirements to `docs/requirements.md` file.