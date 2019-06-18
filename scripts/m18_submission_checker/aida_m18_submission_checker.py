'''
This script is designed to parse an archive [.zip, .tgz, .tar.gz] and determine if it is properly structured, and will
work with Next Century's validator.

To run:
python3 aida_m18_submission_checker.py <path/to/archive>

Output will be printed to stdout and/or stderr
'''
import sys
import os
import tarfile, zipfile
import re
import logging


'''
Globals
'''

#variable to store if we use tar or zip method to access the file
#-1 = error. cannot continue
#0 = .tar.gz, or .tgz
#1 = .zip
FILETYPE = -1

#task types to validate for
TASK_TYPES = ["1a", "1b", "2", "3"]

#the base name of the archive file
ARCHIVE_FILE_NAME = ''

#list of names of items in the archive - to be populated after determining what filetype the archive is
ARCHIVE_NAMES = []

def do_TA1a_check():
    """
    Check the archive
    TASK 1a Directory structure:
    <TA1aperformer>_<run>
        NIST
            <document_id>.ttl                  (1 to X)
		INTER-TA
		    <document_id>.ttl                  (0 to X)

    Ignores non ttl files.
    Prints number of ttl files found, and number of ttl files that are in need to be checked.

    :return: True if it appears submit ready, False otherwise
    :rtype: bool
    """

    ttls_found_count = 0
    ttls_error_warning_count = 0
    ttls_valid_count = 0

    for name in ARCHIVE_NAMES:

        #split into levels
        path_items = re.split('/', name)

#TODO CHECK TA1A AND TA1B FOR IF PATH_ITEMS[-1] IS EVER NOT THE LOCATION OF THE .TTL FILE NAME

        if path_items[-1].endswith('.ttl'):

            ttls_found_count += 1

            #validate that the ttl file is in an acceptable directory
            #rule: ttl needs to be in 2nd level -- aka 3rd item in list['name', 'NIST', 'valid.ttl']
            #rule: ttl needs to be in directory named NIST, or INTER-TA
            try:
                if ((path_items[-2] == 'NIST' or path_items[-2] == 'INTER-TA') and len(path_items) == 3):
                    ttls_valid_count = ttls_valid_count + 1
                else:
                    ttls_error_warning_count += 1
            except:
                ttls_error_warning_count += 1

    logging.info("\033[1mTA_1a\033[0m Archive Validator report:")
    logging.info("TTL files found: " + str(ttls_found_count))
    logging.info("TTL files in valid locations: " + str(ttls_valid_count))
    if ttls_error_warning_count > 0:
        logging.error("TTL files found in an invalid location: " + str(ttls_error_warning_count))
        logging.error(".ttl files must be located in either the <run_ID>/NIST/ or <run_id>/INTER-TA directory")

    if ttls_found_count > 0 and ttls_error_warning_count == 0:
        return True
    return False


def do_TA1b_check():
    """
    Task 1b directory structure:
    <TA1performer>_<run>
        NIST
            hypothesisID			(1 to Y)
                <document_id>.ttl 		(1 to X)

    Ignores non ttl files.
    Prints number of ttl files found, and number of ttl files that are in need to be checked.

    :return: True if it appears submit ready, False otherwise
    :rtype: bool
    """
    ttls_found_count = 0
    ttls_error_warning_count = 0
    ttls_valid_count = 0

    for name in ARCHIVE_NAMES:

        #split into levels
        path_items = re.split('/', name)

        if path_items[-1].endswith('.ttl'):

            ttls_found_count += 1

            #validate that the ttl file is in an acceptable directory
            #rule: ttl needs to be in 3rd level -- aka 4th item in list['name', 'NIST', 'hypothesis', 'valid.ttl']
            #rule: ttl needs to be in a subdirectory within the NIST directory
            try:
                if path_items[-3] == 'NIST' and len(path_items) == 4:
                    ttls_valid_count = ttls_valid_count + 1
                else:
                   ttls_error_warning_count += 1
            except:
                ttls_error_warning_count += 1

    logging.info("\033[1mTA_1b\033[0m Archive Validator report:")
    logging.info("TTL files found: " + str(ttls_found_count))
    logging.info("TTL files in valid locations: " + str(ttls_valid_count))
    if ttls_error_warning_count > 0:
        logging.error("TTL files found in an invalid location: " + str(ttls_error_warning_count))
        logging.error("ttl files must be located the appropriate hypothesis_id subdirectory of the <run_ID>/NIST/ directory. i.e. <run_id>/NIST/<hypothesis_id>/file.ttl")

    if ttls_found_count > 0 and ttls_error_warning_count == 0:
        return True
    return False


def do_TA2_check():
    """
    Task 2 directory structure:
    <TA1performer>_<run>(-<TA1performer>_<run>).<TA2performer>_<run>
        NIST
            <TA1performer>_<run>(-<TA1performer>_<run>).<TA2performer>_<run>.ttl	(1)
        INTER-TA
            <TA1performer>_<run>(-<TA1performer>_<run>).<TA2performer>_<run>.ttl	(0 or 1)


    Ignores non ttl files.
    Prints number of ttl files found, and number of ttl files that are in need to be checked.

    we do not check for all NIST standards set by the document, only for basic directory structure and a NIST/INTER-TA directory
    :return: True if it appears submit ready, False otherwise
    :rtype: bool
    """

    ttls_found_count = 0
    ttls_error_warning_count = 0
    ttls_valid_count = 0

    for name in ARCHIVE_NAMES:

        #split into levels
        path_items = re.split('/', name)

        if path_items[-1].endswith('.ttl'):

            ttls_found_count += 1

            #validate that the ttl file is in an acceptable directory
            #rule: ttl needs to be in 2nd level -- aka 3rd item in list['name', 'NIST', 'valid.ttl']
            #rule: ttl needs to be in directory named NIST, or INTER-TA
            try:
                if ((path_items[-2] == 'NIST' or path_items[-2] == 'INTER-TA') and len(path_items) == 3):
                    ttls_valid_count = ttls_valid_count + 1
                else:
                    ttls_error_warning_count += 1
            except:
                ttls_error_warning_count += 1


    logging.info("\033[1mTA_2\033[0m Archive Validator report:")
    logging.info("TTL files found: " + str(ttls_found_count))
    logging.info("TTL files in valid locations: " + str(ttls_valid_count))
    if ttls_error_warning_count > 0:
        logging.error("TTL files found in an invalid location: " + str(ttls_error_warning_count))
        logging.error(".ttl files must be located in either the <run_ID>/NIST/ or <run_id>/INTER-TA directory")

    if ttls_found_count > 0 and ttls_error_warning_count == 0:
        return True
    return False


def do_TA3_check():
    """
    Task 3 directory structure:
    <TA1performer>_<run>(-<TA1performer>_<run>).<TA2performer>_<run>(-<TA2performer>_<run>).<TA3performer>_<run>
        <TA1performer>_<run>(-<TA1performer>_<run>).<TA2performer>_<run>(-<TA2performer>_<run>).<TA3performer>_<run>.<SIN ID>.<SIN frame ID>.<H followed by three digits>.ttl			(1 to X)

    Ignores non ttl files.
    Prints number of ttl files found, and number of ttl files that are in need to be checked.

    we do not check for all NIST standards set by the document, only for basic directory structure.
    :return: True if it appears submit ready, False otherwise
    :rtype: bool
    """

    ttls_found_count = 0
    ttls_error_warning_count = 0
    ttls_valid_count = 0

    for name in ARCHIVE_NAMES:

        #split into levels
        path_items = re.split('/', name)

        if path_items[-1].endswith('.ttl'):

            ttls_found_count += 1

            #validate that the ttl file is in an acceptable directory
            #rule: ttl needs to be in 2nd level -- aka 3rd item in list['name', 'NIST', 'valid.ttl']
            #rule: ttl needs to be in directory named NIST, or INTER-TA

            if len(path_items) == 2:
                ttls_valid_count += 1
            else:
                ttls_error_warning_count += 1

    logging.info("\033[1mTA_3\033[0m Archive Validator report:")
    logging.info("TTL files found: " + str(ttls_found_count))
    logging.info("TTL files in valid locations: " + str(ttls_valid_count))
    if ttls_error_warning_count > 0:
        logging.error("TTL files found in an invalid location: " + str(ttls_error_warning_count))
        logging.error(".ttl files must be located in either the <run_ID>/NIST/ or <run_id>/INTER-TA directory")

    if ttls_found_count > 0 and ttls_error_warning_count == 0:
        return True
    return False


def get_task_type():
    """
    :return: int corresponding to the task type depending on the archive file name (see TASK_TYPE)
    :rtype: int
    """

    #user did not tell us what task type it was, we must determine it ourselves.
    #count the number of .'s in the archive file name to determine the task type. Hope the user has the proper naming convention.
    basename = os.path.basename(ARCHIVE_FILE_NAME)

    #get rid of extra . count if the file extention is .tar.gz
    dot_count = basename.count('.') if not basename.endswith('.tar.gz') else basename.count('.') - 1

    if dot_count == 1:
        #if theres 1 dot_count, we need to check to see if it is 1a or 1b.

        '''
        determining if 1a or 1b:
            if .ttl file in "NIST" directory then 1a --> .*\/NIST\/[^\/]+.ttl   mode=/i
            if directory in "NIST" directory and no .ttl in "NIST" directory, then 1b   --> .*\/NIST\/\S*\/\S*\.ttl mode =/i
            
        '''
        #type count and ta1_temp used to determine if 1a or 1b
        type_count = 0
        ta1_ret_val = 0

        names_str = " ".join(ARCHIVE_NAMES)
        ta1a_dir_struct_regex = re.compile(".*\/NIST\/[^\/]+.ttl", re.IGNORECASE)
        ta1b_dir_struct_regex = re.compile(".*\/NIST\/\S*\/\S*\.ttl", re.IGNORECASE)

        #if ttls located in the NIST directory, mark as potential TA_1a
        if ta1a_dir_struct_regex.search(names_str) is not None:
            ta1_ret_val = 0
            type_count = type_count + 1
        #if ttls located at some subdirectory of the NIST directory, mark as potential TA_1b
        if ta1b_dir_struct_regex.search(names_str) is not None:
            ta1_ret_val = 1
            type_count = type_count + 1
        #if ttls were found in both the NIST/ directory and a subdirectory of NIST/, mark as TA_1a and have the ta_1a do this check too
        if ta1_ret_val > -1 and type_count > 1 :        #use > and not == for scaleability? idk dont @ me c:
            logging.warning("WARNING: Subdirectories found at the same level of .ttl files in the NIST directory.")
            ta1_ret_val = 0
        return ta1_ret_val
    elif dot_count > 1 and dot_count < 4:
        return dot_count
    else:
        logging.error("Cannot figure out the TA level. Please ensure archive name is properly titled per NIST guidelines.")
        return -1



def validate_archive_filetype():
    """
    Checks the archive file type. If it is not a valid filetype, the program will
    print an error message and quit the script.
    Valid filetypes: .tgz, .tar.gz, or .zip.

    :return: True if valid filetype and successfully set the global FILETYPE variable, False otherwise
    :rtype: bool
    """
    global FILETYPE

    if ARCHIVE_FILE_NAME.endswith('.tar.gz') or ARCHIVE_FILE_NAME.endswith('.tgz'):
        FILETYPE = 0
    elif ARCHIVE_FILE_NAME.endswith('.zip'):
        FILETYPE = 1
    else:
        FILETYPE = -1
        return False

    return True

def main():
    """

    :returns: True if script completed successfully, False otherwise
    :rtype: bool
    """
    global FILETYPE
    global TASK_TYPES
    global ARCHIVE_NAMES
    global ARCHIVE_FILE_NAME

    # set logging to log to stdout
    logging.basicConfig(level='INFO', format='%(levelname)s:  %(message)s')

    #check runtime arguments
    if len(sys.argv) < 2:
        logging.error("Missing archive file as argument.\nTo run: python3 aida_m18_submission_checker.py <path/to/archive>")
        return False
    if not os.path.isfile(sys.argv[1]):
        logging.error("Argument is not a file.\nTo run: python3 aida_m18_submission_checker.py <path/to/archive>")
        return False

    ARCHIVE_FILE_NAME = str(sys.argv[1])

    #ensure the archive type is a valid filetype and set the global FILETYPE value
    if validate_archive_filetype() < 0:
        logging.critical("Archive filetype unknown. Please use .zip, .tgz, or .tar.gz")
        return False

    #get names of items in the archive
    try:
        logging.info("Checking archive... this may take a moment for large files.")
        archive = zipfile.ZipFile(ARCHIVE_FILE_NAME, 'r', allowZip64=True) if FILETYPE else tarfile.open(ARCHIVE_FILE_NAME, mode='r')
        ARCHIVE_NAMES = archive.namelist() if FILETYPE == 1 else archive.getnames()
    except:
        #expected errors: (zipfile.BadZipFile, tarfile.ReadError), but catch all of them anyways
        logging.critical("Error thrown attempting to read/open the archive. Please use 'zip' or 'tar' to create your archive.")
        return False


    task_type = get_task_type()
    if task_type == -1:
        logging.error("Cannot determine the task type! Please input task type to arguments and run again.")
        return False

    logging.info("archive " + str(ARCHIVE_FILE_NAME) + " is detected to be of task type \033[1m" + TASK_TYPES[task_type] + "\033[0m.")

    check_status = False
    if task_type == 0:
        check_status = do_TA1a_check()
    elif task_type == 1:
        check_status = do_TA1b_check()
    elif task_type == 2:
        check_status = do_TA2_check()
    elif task_type == 3:
        check_status = do_TA3_check()


    is_or_not = " is " if check_status else " is not "
    result_str = "\033[1mVALID\033[0m and submit ready." if check_status else "\033[1mINVALID\033[0m."
    logging.info("Submission: " + ARCHIVE_FILE_NAME + " is " + result_str)

    logging.info("Task Type: \033[1m{0}\033[0m | ".format(TASK_TYPES[task_type]) * 10)

    return check_status


if __name__ == "__main__": main()
