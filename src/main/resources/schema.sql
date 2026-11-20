CREATE TABLE IF NOT EXISTS tblUser (
 id INT PRIMARY KEY AUTO_INCREMENT, username VARCHAR(100) UNIQUE NOT NULL, passwordHash VARCHAR(255) NOT NULL,
 fullName VARCHAR(100) NOT NULL, dateOfBirth DATE, address VARCHAR(255), phone VARCHAR(100), email VARCHAR(100) UNIQUE, active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE IF NOT EXISTS tblMember (
 id INT PRIMARY KEY, memberCode VARCHAR(100) UNIQUE, registrationDate DATE, cardIssueDate DATE, cardExpiryDate DATE, cardStatus VARCHAR(20) NOT NULL DEFAULT 'NONE', FOREIGN KEY(id) REFERENCES tblUser(id)
);
CREATE TABLE IF NOT EXISTS tblLibrarian (id INT PRIMARY KEY, employeeCode VARCHAR(100) UNIQUE NOT NULL, position VARCHAR(100), FOREIGN KEY(id) REFERENCES tblUser(id));
CREATE TABLE IF NOT EXISTS tblManager (id INT PRIMARY KEY, employeeCode VARCHAR(100) UNIQUE NOT NULL, managementLevel VARCHAR(100), FOREIGN KEY(id) REFERENCES tblUser(id));
CREATE TABLE IF NOT EXISTS tblDocument (
 id INT PRIMARY KEY AUTO_INCREMENT, documentCode VARCHAR(100) NOT NULL UNIQUE, title VARCHAR(255) NOT NULL, author VARCHAR(100), publisher VARCHAR(100),
 publishYear INT, category VARCHAR(100) NOT NULL, shelfLocation VARCHAR(100), totalQuantity INT NOT NULL DEFAULT 0 CHECK(totalQuantity>=0), active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE IF NOT EXISTS tblSupplier (id INT PRIMARY KEY AUTO_INCREMENT, supplierCode VARCHAR(100) UNIQUE NOT NULL, supplierName VARCHAR(100) NOT NULL, address VARCHAR(255), phone VARCHAR(100), email VARCHAR(100), active BOOLEAN NOT NULL DEFAULT TRUE);
CREATE TABLE IF NOT EXISTS tblBorrowOrder (id INT PRIMARY KEY AUTO_INCREMENT, orderCode VARCHAR(100) UNIQUE NOT NULL, borrowDate DATETIME NOT NULL, dueDate DATE NOT NULL, memberId INT NOT NULL, librarianId INT NOT NULL, FOREIGN KEY(memberId) REFERENCES tblMember(id), FOREIGN KEY(librarianId) REFERENCES tblLibrarian(id));
CREATE TABLE IF NOT EXISTS tblBorrowOrderDetail (id INT PRIMARY KEY AUTO_INCREMENT, borrowOrderId INT NOT NULL, documentId INT NOT NULL, quantity INT NOT NULL CHECK(quantity>0), UNIQUE(borrowOrderId,documentId), FOREIGN KEY(borrowOrderId) REFERENCES tblBorrowOrder(id), FOREIGN KEY(documentId) REFERENCES tblDocument(id));
CREATE TABLE IF NOT EXISTS tblReturnOrder (id INT PRIMARY KEY AUTO_INCREMENT, returnCode VARCHAR(100) UNIQUE NOT NULL, returnDate DATETIME NOT NULL, borrowOrderId INT NOT NULL, librarianId INT NOT NULL, FOREIGN KEY(borrowOrderId) REFERENCES tblBorrowOrder(id), FOREIGN KEY(librarianId) REFERENCES tblLibrarian(id));
CREATE TABLE IF NOT EXISTS tblReturnOrderDetail (id INT PRIMARY KEY AUTO_INCREMENT, returnOrderId INT NOT NULL, borrowOrderDetailId INT NOT NULL, returnedQuantity INT NOT NULL CHECK(returnedQuantity>0), UNIQUE(returnOrderId,borrowOrderDetailId), FOREIGN KEY(returnOrderId) REFERENCES tblReturnOrder(id), FOREIGN KEY(borrowOrderDetailId) REFERENCES tblBorrowOrderDetail(id));
CREATE TABLE IF NOT EXISTS tblImportOrder (id INT PRIMARY KEY AUTO_INCREMENT, importCode VARCHAR(100) UNIQUE NOT NULL, importDate DATETIME NOT NULL, supplierId INT NOT NULL, librarianId INT NOT NULL, paymentStatus VARCHAR(20) NOT NULL, paidAt DATETIME, submissionToken VARCHAR(100) UNIQUE NOT NULL, FOREIGN KEY(supplierId) REFERENCES tblSupplier(id), FOREIGN KEY(librarianId) REFERENCES tblLibrarian(id));
CREATE TABLE IF NOT EXISTS tblImportOrderDetail (id INT PRIMARY KEY AUTO_INCREMENT, importOrderId INT NOT NULL, documentId INT NOT NULL, quantity INT NOT NULL CHECK(quantity>0), importPrice DECIMAL(15,2) NOT NULL CHECK(importPrice>0), UNIQUE(importOrderId,documentId), FOREIGN KEY(importOrderId) REFERENCES tblImportOrder(id), FOREIGN KEY(documentId) REFERENCES tblDocument(id));